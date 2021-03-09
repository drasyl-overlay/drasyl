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
package org.drasyl.util;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.drasyl.util.PlatformDependent.javaSpecificationVersion;
import static org.drasyl.util.PlatformDependent.javaVersion;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PlatformDependentTest {
    @Nested
    class JavaVersion {
        @Test
        void shouldReturnVersion() {
            assertThat(javaVersion(), greaterThan(0));
        }
    }

    @Nested
    class UnsafeStaticFieldOffsetSupported {
        @SuppressWarnings("ResultOfMethodCallIgnored")
        @Test
        void shouldNotThrowException() {
            assertDoesNotThrow((Executable) PlatformDependent::unsafeStaticFieldOffsetSupported);
        }
    }

    @Nested
    class JavaSpecificationVersion {
        @Test
        void shouldReturnVersion() {
            assertThat(javaSpecificationVersion(), greaterThan(0));
        }
    }
}
