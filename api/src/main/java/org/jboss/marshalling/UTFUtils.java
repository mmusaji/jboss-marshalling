/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.marshalling;

import java.io.IOException;
import java.io.UTFDataFormatException;
import java.io.EOFException;

/**
 * Handy utility methods for dealing with strings in the modified UTF-8 format.
 */
public final class UTFUtils {
    private static final String INVALID_BYTE = "Invalid byte";
    private static final String MALFORMED = "Malformed UTF-8 sequence";

    private UTFUtils() {
    }

    /**
     * Get the number of bytes used by the modified UTF-8 encoded form of the given string.  If the length is
     * greater than {@code 65536}, an exception is thrown.
     *
     * @param s the string
     * @return the length
     * @throws UTFDataFormatException if the string is longer than {@code 65536} characters
     */
    public static int getShortUTFLength(final String s) throws UTFDataFormatException {
        final int length = s.length();
        int l = 0;
        for (int i = 0; i < length; i ++) {
            final char c = s.charAt(i);
            if (c > 0 && c <= 0x7f) {
                l ++;
            } else if (c <= 0x07ff) {
                l += 2;
            } else {
                l += 3;
            }
            if (l > 65535) {
                throw new UTFDataFormatException("String is too long for writeUTF");
            }
        }
        return l;
    }

    /**
     * Get the number of bytes used by the modified UTF-8 encoded form of the given string.
     *
     * @param s the string
     * @return the length
     */
    public static long getLongUTFLength(final String s) {
        final int length = s.length();
        long l = 0;
        for (int i = 0; i < length; i ++) {
            final char c = s.charAt(i);
            if (c > 0 && c <= 0x7f) {
                l ++;
            } else if (c <= 0x07ff) {
                l += 2L;
            } else {
                l += 3L;
            }
        }
        return l;
    }

    /**
     * Write the modified UTF-8 form of the given string to the given output.
     *
     * @param output the output to write to
     * @param s the string
     * @throws IOException if an I/O error occurs
     */
    public static void writeUTFBytes(final ByteOutput output, final String s) throws IOException {
        final int length = s.length();
        for (int i = 0; i < length; i ++) {
            final char c = s.charAt(i);
            if (c > 0 && c <= 0x7f) {
                output.write(c);
            } else if (c <= 0x07ff) {
                output.write(0xc0 | 0x1f & c >> 6);
                output.write(0x80 | 0x3f & c);
            } else {
                output.write(0xe0 | 0x0f & c >> 12);
                output.write(0x80 | 0x3f & c >> 6);
                output.write(0x80 | 0x3f & c);
            }
        }
    }

    /**
     * Read the given number of characters from the given byte input.  The length given is in characters,
     * <b>NOT</b> in bytes.
     *
     * @param input the byte source
     * @param len the number of characters to read
     * @return the string
     * @throws IOException if an I/O error occurs
     */
    public static String readUTFBytes(final ByteInput input, final int len) throws IOException {
        final char[] chars = new char[len];
        for (int i = 0; i < len; i ++) {
            final int c = readUTFChar(input);
            chars[i] = c == -1 ? 0 : (char) c;
        }
        return String.valueOf(chars);
    }

    /**
     * Read the given number of characters from the given byte input.  The length given is in bytes.
     *
     * @param input the byte source
     * @param len the number of bytes to read
     * @return the string
     * @throws IOException if an I/O error occurs
     */
    public static String readUTFBytesByByteCount(final ByteInput input, final long len) throws IOException {
        final StringBuilder builder = new StringBuilder();
        for (long i = 0; i < len; i ++) {
            final int a = input.read();
            if (a < 0) {
                throw new EOFException();
            } else if (a == 0) {
                builder.append('\0');
            } else if (a < 0x80) {
                builder.append((char) a);
            } else if (a < 0xc0) {
                throw new UTFDataFormatException(INVALID_BYTE);
            } else if (a < 0xe0) {
                if (++i < len) {
                    final int b = input.read();
                    if (b == -1) {
                        throw new EOFException();
                    } else if ((b & 0xc0) != 0x80) {
                        throw new UTFDataFormatException(INVALID_BYTE);
                    }
                    builder.append((char) ((a & 0x1f) << 6 | b & 0x3f));
                } else {
                    throw new UTFDataFormatException(MALFORMED);
                }
            } else if (a < 0xf0) {
                if (++i < len) {
                    final int b = input.read();
                    if (b == -1) {
                        throw new EOFException();
                    } else if ((b & 0xc0) != 0x80) {
                        throw new UTFDataFormatException(INVALID_BYTE);
                    }
                    if (++i < len) {
                        final int c1 = input.read();
                        if (c1 == -1) {
                            throw new EOFException();
                        } else if ((c1 & 0xc0) != 0x80) {
                            throw new UTFDataFormatException(INVALID_BYTE);
                        }
                        builder.append((char) ((a & 0x0f) << 12 | (b & 0x3f) << 6 | c1 & 0x3f));
                    } else {
                        throw new UTFDataFormatException(MALFORMED);
                    }
                } else {
                    throw new UTFDataFormatException(MALFORMED);
                }
            } else {
                throw new UTFDataFormatException(INVALID_BYTE);
            }
        }
        return builder.toString();
    }

    /**
     * Read a null-terminated modified UTF-8 string from the given byte input.  Bytes are read until a 0 is found or
     * until the end of the stream, whichever comes first.
     *
     * @param input the input
     * @return the string
     * @throws IOException if an I/O error occurs
     */
    public static String readUTFZBytes(final ByteInput input) throws IOException {
        final StringBuilder builder = new StringBuilder();
        for (;;) {
            final int c = readUTFChar(input);
            if (c == -1) {
                return builder.toString();
            }
            builder.append((char) c);
        }
    }

    private static int readUTFChar(final ByteInput input) throws IOException {
        final int a = input.read();
        if (a < 0) {
            throw new EOFException();
        } else if (a == 0) {
            return -1;
        } else if (a < 0x80) {
            return (char)a;
        } else if (a < 0xc0) {
            throw new UTFDataFormatException(INVALID_BYTE);
        } else if (a < 0xe0) {
            final int b = input.read();
            if (b == -1) {
                throw new EOFException();
            } else if ((b & 0xc0) != 0x80) {
                throw new UTFDataFormatException(INVALID_BYTE);
            }
            return (a & 0x1f) << 6 | b & 0x3f;
        } else if (a < 0xf0) {
            final int b = input.read();
            if (b == -1) {
                throw new EOFException();
            } else if ((b & 0xc0) != 0x80) {
                throw new UTFDataFormatException(INVALID_BYTE);
            }
            final int c = input.read();
            if (c == -1) {
                throw new EOFException();
            } else if ((c & 0xc0) != 0x80) {
                throw new UTFDataFormatException(INVALID_BYTE);
            }
            return (a & 0x0f) << 12 | (b & 0x3f) << 6 | c & 0x3f;
        } else {
            throw new UTFDataFormatException(INVALID_BYTE);
        }
    }
}