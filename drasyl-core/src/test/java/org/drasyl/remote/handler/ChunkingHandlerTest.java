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
package org.drasyl.remote.handler;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(MockitoExtension.class)
class ChunkingHandlerTest {
    @Nested
    class OnIngoingMessage {
        @Nested
        class WhenAddressedToMe {
            @Test
            void shouldPassthroughNonChunkedMessage() {
                fail("not implemented");
            }

            @Test
            void shouldCacheChunkedMessageIfOtherChunksAreStillMissing() {
                fail("not implemented");
            }

            @Test
            void shouldBuildMessageAfterReceivingLastMissingChunk() {
                fail("not implemented");
            }
        }

        @Nested
        class WhenNotAddressedToMe {
            @Test
            void shouldPassthroughNonChunkedMessage() {
                fail("not implemented");
            }

            @Test
            void shouldPassthroughChunkedMessage() {
                fail("not implemented");
            }
        }
    }

    @Nested
    class OnOutgoingMessage {
        @Nested
        class FromMe {
            @Test
            void shouldPassthroughMessageNotExceedingMtuSize() {
                fail("not implemented");
            }

            @Test
            void shouldChunkMessageExceedingMtuSize() {
                fail("not implemented");
            }
        }

        @Nested
        class NotFromMe {
            @Test
            void shouldPassthroughNonChunkedMessage() {
                fail("not implemented");
            }

            @Test
            void shouldPassthroughChunkedMessage() {
                fail("not implemented");
            }
        }
    }
}