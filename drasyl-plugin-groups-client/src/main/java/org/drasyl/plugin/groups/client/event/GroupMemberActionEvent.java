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
package org.drasyl.plugin.groups.client.event;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.plugin.groups.client.Group;

import java.util.Objects;

import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;

@SuppressWarnings("java:S118")
abstract class GroupMemberActionEvent implements GroupEvent {
    protected final CompressedPublicKey member;
    protected final Group group;

    protected GroupMemberActionEvent(
            final CompressedPublicKey member, final Group group) {
        this.member = requireNonNull(member);
        this.group = requireNonNull(group);
    }

    public CompressedPublicKey getMember() {
        return member;
    }

    @Override
    public Group getGroup() {
        return group;
    }

    @Override
    public int hashCode() {
        return hash(member, group);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GroupMemberActionEvent that = (GroupMemberActionEvent) o;
        return Objects.equals(member, that.member) &&
                Objects.equals(group, that.group);
    }
}
