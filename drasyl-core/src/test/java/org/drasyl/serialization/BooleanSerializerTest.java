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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BooleanSerializerTest {
    private BooleanSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new BooleanSerializer();
    }

    @Nested
    class ToByteArray {
        @Test
        void shouldSerializeTrueToCorrectByteArray() throws IOException {
            final byte[] bytes = serializer.toByteArray(true);

            assertArrayEquals(new byte[]{ 1 }, bytes);
        }

        @Test
        void shouldSerializeFalseToCorrectByteArray() throws IOException {
            final byte[] bytes = serializer.toByteArray(false);

            assertArrayEquals(new byte[]{ 0 }, bytes);
        }

        @Test
        void shouldThrowExceptionForNonString() {
            assertThrows(IOException.class, () -> serializer.toByteArray(1337));
        }
    }

    @Nested
    class FromByteArray {
        @Test
        void shouldDeserializeTrueByteArrayToTrue() throws IOException {
            assertTrue(serializer.fromByteArray(new byte[]{ 1 }, Boolean.class));
        }

        @Test
        void shouldDeserializeFalseByteArrayToFalse() throws IOException {
            assertFalse(serializer.fromByteArray(new byte[]{ 0 }, Boolean.class));
        }

        @Test
        void shouldThrowExceptionForNonProtobufType() {
            assertThrows(IOException.class, () -> serializer.fromByteArray(new byte[]{}, String.class));
        }
    }
}
