/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.serialization;

import java.io.IOException;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * A Serializer represents a bimap between an object and an array of bytes representing that
 * object.
 *
 * <p>Make sure that your implementation implements the standard constructor!
 */
public interface Serializer {
    /**
     * Serializes the given object into an array of bytes
     *
     * @throws IOException if deserialization from byte array fails
     */
    byte[] toByteArray(Object o) throws IOException;

    /**
     * Produces an object of type {@code T} from an array of bytes.
     *
     * @throws IOException if serialization to byte array fails
     */
    <T> T fromByteArray(byte[] bytes, Class<T> type) throws IOException;

    @SuppressWarnings("java:S2658")
    default Object fromByteArray(final byte[] bytes, final String typeName) throws IOException {
        try {
            return fromByteArray(bytes, !isNullOrEmpty(typeName) ? Class.forName(typeName) : null);
        }
        catch (final ClassNotFoundException e) {
            throw new IOException("Class with name `" + typeName + "` could not be located.", e);
        }
    }
}
