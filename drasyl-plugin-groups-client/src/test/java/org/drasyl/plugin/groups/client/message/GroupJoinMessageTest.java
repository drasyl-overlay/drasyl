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
package org.drasyl.plugin.groups.client.message;

import org.drasyl.identity.ProofOfWork;
import org.drasyl.plugin.groups.client.Group;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class GroupJoinMessageTest {
    @Mock
    private ProofOfWork proofOfWork;

    @Nested
    class Constructor {
        @Test
        void shouldRejectNullValues() {
            final Group group = Group.of("my-squad");
            assertThrows(NullPointerException.class, () -> new GroupJoinMessage(group, "", null, false));
            assertThrows(NullPointerException.class, () -> new GroupJoinMessage(null, "", proofOfWork, false));
            assertThrows(NullPointerException.class, () -> new GroupJoinMessage(group, null, proofOfWork, false));
        }

        @Test
        void shouldReturnCorrectCredentials() {
            final GroupJoinMessage msg = new GroupJoinMessage(Group.of("my-squad"), "", proofOfWork, false);

            assertEquals("", msg.getCredentials());
        }

        @Test
        void shouldReturnCorrectGroupName() {
            final GroupJoinMessage msg = new GroupJoinMessage(Group.of("my-squad"), "", proofOfWork, false);

            assertEquals(Group.of("my-squad"), msg.getGroup());
        }

        @Test
        void shouldReturnCorrectProofOfWork() {
            final GroupJoinMessage msg = new GroupJoinMessage(Group.of("my-squad"), "", proofOfWork, false);

            assertEquals(proofOfWork, msg.getProofOfWork());
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldBeEquals() {
            final GroupJoinMessage msg1 = new GroupJoinMessage(Group.of("my-squad"), "", proofOfWork, false);
            final GroupJoinMessage msg2 = new GroupJoinMessage(Group.of("my-squad"), "", proofOfWork, false);

            assertEquals(msg1, msg2);
        }

        @Test
        void shouldNotBeEquals() {
            final GroupJoinMessage msg1 = new GroupJoinMessage(Group.of("my-squad"), "a", proofOfWork, false);
            final GroupJoinMessage msg2 = new GroupJoinMessage(Group.of("your-squad"), "b", proofOfWork, false);

            assertNotEquals(msg1, msg2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldBeEquals() {
            final GroupJoinMessage msg1 = new GroupJoinMessage(Group.of("my-squad"), "", proofOfWork, false);
            final GroupJoinMessage msg2 = new GroupJoinMessage(Group.of("my-squad"), "", proofOfWork, false);

            assertEquals(msg1.hashCode(), msg2.hashCode());
        }

        @Test
        void shouldNotBeEquals() {
            final GroupJoinMessage msg1 = new GroupJoinMessage(Group.of("my-squad"), "a", proofOfWork, false);
            final GroupJoinMessage msg2 = new GroupJoinMessage(Group.of("your-squad"), "b", proofOfWork, false);

            assertNotEquals(msg1.hashCode(), msg2.hashCode());
        }
    }
}
