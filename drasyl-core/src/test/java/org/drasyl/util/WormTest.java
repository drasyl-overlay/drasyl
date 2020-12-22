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

import java.util.NoSuchElementException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class WormTest {
    @Nested
    class Get {
        @Test
        void shouldReturnValueIfPresent() {
            final Worm<String> worm = new Worm<>("Merry Christmas!");

            assertEquals("Merry Christmas!", worm.get());
        }

        @Test
        void shouldReturnValueIfNullValuePresent() {
            final Worm<String> worm = new Worm<>(null);

            assertNull(worm.get());
        }

        @Test
        void shouldThrowExceptionIfEmpty() {
            final Worm<String> worm = new Worm<>();

            assertThrows(NoSuchElementException.class, worm::get);
        }
    }

    @Nested
    class Set {
        @Test
        void shouldSetValueIfWormIsEmpty() {
            final Worm<String> worm = new Worm<>();

            assertDoesNotThrow(() -> worm.set("Hello World"));
            assertEquals("Hello World", worm.get());
        }

        @Test
        void shouldAbleToSetValueToNull() {
            final Worm<String> worm = new Worm<>();

            assertDoesNotThrow(() -> worm.set(null));
            assertNull(worm.get());
        }

        @Test
        void shouldThrowExceptionIfWormIsNotEmpty() {
            final Worm<String> worm = new Worm<>("Alice");

            assertThrows(IllegalStateException.class, () -> worm.set("Bob"));
        }
    }

    @Nested
    class GetOrSet {
        @Test
        void shouldSetAndReturnValueIfWormWasEmpty() {
            final Worm<String> worm = new Worm<>();

            assertEquals("Bob", worm.getOrSet("Bob"));
        }

        @Test
        void shouldAbleToSetValueToNull() {
            final Worm<String> worm = new Worm<>();

            assertNull(worm.getOrSet(null));
        }

        @Test
        void shouldReturnExistingValueIfWormWasNotEmpty() {
            final Worm<String> worm = new Worm<>("Alice");

            assertEquals("Alice", worm.getOrSet("Bob"));
        }
    }

    @Nested
    class GetOrCompute {
        @Test
        void shouldComputeAndReturnValueIfWormWasEmpty() {
            final Worm<String> worm = new Worm<>();

            assertEquals("Bob", worm.getOrCompute(() -> "Bob"));
        }

        @Test
        void shouldAbleToComputeValueToNull() {
            final Worm<String> worm = new Worm<>();

            assertNull(worm.getOrCompute(() -> null));
        }

        @Test
        void shouldReturnExistingValueIfWormWasNotEmpty() {
            final Worm<String> worm = new Worm<>("Alice");

            assertEquals("Alice", worm.getOrCompute(() -> "Bob"));
        }
    }

    @Nested
    class IsPresent {
        @Test
        void shouldReturnTrueForNonEmptyWorm() {
            final Worm<String> worm = new Worm<>("Alice");

            assertTrue(worm.isPresent());
        }

        @Test
        void shouldReturnFalseForEmptyWorm() {
            final Worm<String> worm = new Worm<>();

            assertFalse(worm.isPresent());
        }
    }

    @Nested
    class IsEmpty {
        @Test
        void shouldReturnFalseForNonEmptyWorm() {
            final Worm<String> worm = new Worm<>("Alice");

            assertFalse(worm.isEmpty());
        }

        @Test
        void shouldReturnTrueForEmptyWorm() {
            final Worm<String> worm = new Worm<>();

            assertTrue(worm.isEmpty());
        }
    }

    @Nested
    class Equals {
        @Test
        void notSameBecauseOfText() {
            final Worm<String> worm1 = new Worm<>("foo");
            final Worm<String> worm2 = new Worm<>("foo");
            final Worm<String> worm3 = new Worm<>("bar");

            assertEquals(worm1, worm2);
            assertNotEquals(worm2, worm3);
        }
    }

    @Nested
    class HashCode {
        @Test
        void notSameBecauseOfText() {
            final Worm<String> worm1 = new Worm<>("foo");
            final Worm<String> worm2 = new Worm<>("foo");
            final Worm<String> worm3 = new Worm<>("bar");

            assertEquals(worm1.hashCode(), worm2.hashCode());
            assertNotEquals(worm2.hashCode(), worm3.hashCode());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldReturnString() {
            final Worm<String> worm = new Worm<>("Alice");

            assertThat(worm.toString(), containsString("Alice"));
        }
    }

    @Nested
    class Of {
        @Test
        void shouldCreateWormWithValue() {
            final Worm<String> worm = Worm.of("Frohe Weihnachten!");

            assertEquals(new Worm<>("Frohe Weihnachten!"), worm);
        }

        @Test
        void shouldCreateWormWithNoValue() {
            final Worm<String> worm = Worm.of();

            assertEquals(new Worm<>(), worm);
        }

        @Test
        void shouldDistinguishBetweenNullAndEmpty() {
            final Worm<String> worm = Worm.of(null);

            assertEquals(new Worm<>(null), worm);
        }
    }
}