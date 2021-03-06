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
package org.drasyl.pipeline.serialization;

import org.drasyl.serialization.NullSerializer;
import org.drasyl.serialization.Serializer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;

@ExtendWith(MockitoExtension.class)
class SerializationTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ReadWriteLock lock;

    @Nested
    class FindSerializerFor {
        @Test
        void shouldReturnSerializerIfSerializerForConcreteClassExist(@Mock final Serializer serializer) {
            final Serialization serialization = new Serialization(Map.of("my-serializer", serializer), Map.of(String.class, "my-serializer"));

            assertEquals(serializer, serialization.findSerializerFor("Hallo Welt".getClass().getName()));
        }

        @Test
        void shouldReturnSerializerIfSerializerForSuperClassExist(@Mock final Serializer serializer) {
            final Serialization serialization = new Serialization(Map.of("my-serializer", serializer), Map.of(Map.class, "my-serializer"));

            assertEquals(serializer, serialization.findSerializerFor(HashMap.class.getName()));
            assertEquals(serializer, serialization.findSerializerFor(Map.class.getName()));
        }

        @Test
        void shouldReturnSerializerIfSerializerForWrapperExist(@Mock final Serializer serializer) {
            final Serialization serialization = new Serialization(Map.of("my-serializer", serializer), Map.of(
                    Boolean.class, "my-serializer",
                    Character.class, "my-serializer",
                    Byte.class, "my-serializer",
                    Float.class, "my-serializer",
                    Integer.class, "my-serializer",
                    Long.class, "my-serializer",
                    Short.class, "my-serializer"
            ));

            assertEquals(serializer, serialization.findSerializerFor(Boolean.class.getName()));
            assertEquals(serializer, serialization.findSerializerFor(Character.class.getName()));
            assertEquals(serializer, serialization.findSerializerFor(Byte.class.getName()));
            assertEquals(serializer, serialization.findSerializerFor(Float.class.getName()));
            assertEquals(serializer, serialization.findSerializerFor(Integer.class.getName()));
            assertEquals(serializer, serialization.findSerializerFor(Long.class.getName()));
            assertEquals(serializer, serialization.findSerializerFor(Short.class.getName()));
        }

        @Test
        void shouldReturnNullIfNoSerializerExist() {
            final Serialization serialization = new Serialization(Map.of(), Map.of());

            assertNull(serialization.findSerializerFor(HashMap.class.getName()));
        }

        @Test
        void shouldReturnNullSerializerForNullObject() {
            final Serialization serialization = new Serialization(Map.of(), Map.of());

            assertThat(serialization.findSerializerFor(null), instanceOf(NullSerializer.class));
        }
    }

    @Nested
    class AddSerializer {
        @Test
        void shouldAddSerializer() {
            final Serializer serializer = new MySerializer();
            final Serialization serialization = new Serialization(new HashMap<>(), new HashMap<>());

            serialization.addSerializer(HashMap.class, serializer);

            assertEquals(serializer, serialization.findSerializerFor(HashMap.class.getName()));
            assertNull(serialization.findSerializerFor(Map.class.getName()));
        }
    }

    @Nested
    class RemoveSerializer {
        @Test
        void shouldRemoveClazz() {
            final Serialization serialization = new Serialization(lock, new HashMap<>(Map.of(HashMap.class.getName(), new MySerializer(), Map.class.getName(), new MySerializer())));

            serialization.removeSerializer(Map.class);

            assertNull(serialization.findSerializerFor(HashMap.class.getName()));
            assertNull(serialization.findSerializerFor(Map.class.getName()));
        }

        @Test
        void shouldRemoveSerializer() {
            final MySerializer serializer = new MySerializer();
            final Serialization serialization = new Serialization(lock, new HashMap<>(Map.of(HashMap.class.getName(), serializer, Map.class.getName(), serializer)));

            serialization.removeSerializer(serializer);

            assertNull(serialization.findSerializerFor(HashMap.class.getName()));
            assertNull(serialization.findSerializerFor(Map.class.getName()));
        }
    }

    private static class MySerializer implements Serializer {
        @Override
        public byte[] toByteArray(final Object o) throws IOException {
            return new byte[0];
        }

        @Override
        public <T> T fromByteArray(final byte[] bytes, final Class<T> type) throws IOException {
            return null;
        }
    }
}
