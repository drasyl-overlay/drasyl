/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.util;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * This class represents an unsigned short in a rang of [0, 2^16]
 */
public class UnsignedShort {
    private final int value;

    private UnsignedShort(final int value) {
        if (value < 0 || value > 65_535) {
            throw new IllegalArgumentException("Value must be in range of [0, 2^16], but was " + value);
        }

        this.value = value;
    }

    private UnsignedShort(final byte[] value) {
        if (value.length != 2) {
            throw new IllegalArgumentException("Value must be a byte array of length 2, but was of length " + value.length);
        }

        this.value = ByteBuffer.wrap(new byte[]{ 0x0, 0x0, value[0], value[1] }).getInt();
    }

    /**
     * Creates a new {@link UnsignedShort}.
     *
     * @param value the value as integer
     * @return an unaligned short
     * @throws IllegalArgumentException if the value is not in range of [0, 2^16].
     */
    public static UnsignedShort of(final int value) {
        return new UnsignedShort(value);
    }

    /**
     * Creates a new {@link UnsignedShort}.
     *
     * @param value the value as byte array in big-endian (BE) format
     * @return an unaligned short
     * @throws IllegalArgumentException if the value is not in range of [0, 2^16].
     */
    public static UnsignedShort of(final byte[] value) {
        return new UnsignedShort(value);
    }

    /**
     * @return a byte array of length 2.
     */
    public byte[] toBytes() {
        return toByteArray(value);
    }

    /**
     * @return the value as integer.
     */
    public int getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "" + value;
    }

    public UnsignedShort increment() {
        return UnsignedShort.of(value + 1);
    }

    public UnsignedShort decrement() {
        return UnsignedShort.of(value - 1);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UnsignedShort that = (UnsignedShort) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    private byte[] toByteArray(final int value) {
        return new byte[]{ (byte) (value >> 8), (byte) value };
    }
}
