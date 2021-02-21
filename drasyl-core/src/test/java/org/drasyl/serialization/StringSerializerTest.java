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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StringSerializerTest {
    private StringSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new StringSerializer();
    }

    @Nested
    class ToByteArray {
        @Test
        void shouldSerializeStringToByteArray() throws IOException {
            final byte[] bytes = serializer.toByteArray("Hallo Welt");

            assertArrayEquals("Hallo Welt".getBytes(), bytes);
        }

        @Test
        void shouldThrowExceptionForNonString() {
            assertThrows(IOException.class, () -> serializer.toByteArray(1337));
        }
    }

    @Nested
    class FromByteArray {
        @Test
        void shouldDeserializeByteArrayToString() throws IOException {
            final Object o = serializer.fromByteArray("Hallo Welt".getBytes(), String.class);

            assertEquals("Hallo Welt", o);
        }

        @Test
        void shouldThrowExceptionForNonString() {
            assertThrows(IOException.class, () -> serializer.fromByteArray(new byte[]{}, Integer.class));
        }
    }
}