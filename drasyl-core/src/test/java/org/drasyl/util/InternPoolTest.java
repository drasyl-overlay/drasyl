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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertSame;

@ExtendWith(MockitoExtension.class)
class InternPoolTest {
    @Nested
    class Intern {
        @Test
        void shouldReturnEqualObjectIfPoolContainsEqualObject() {
            final MyObject a = new MyObject("Hello World");
            final MyObject b = new MyObject("Hello World");

            final InternPool<MyObject> pool = new InternPool<>();
            pool.intern(a);

            assertSame(a, pool.intern(b));
        }

        @Test
        void shouldReturnSameObjectIfPoolDoesNotContainsEqualObject() {
            final MyObject a = new MyObject("Hello World");

            final InternPool<MyObject> pool = new InternPool<>();

            assertSame(a, pool.intern(a));
        }
    }

    static class MyObject {
        private final String value;

        public MyObject(final String value) {
            this.value = value;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final MyObject myObject = (MyObject) o;
            return Objects.equals(value, myObject.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }
}