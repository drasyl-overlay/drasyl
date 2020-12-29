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

package org.drasyl.event;

/**
 * This event signals that the node has successfully registered with the super peer. If a node has
 * been configured with no super peer (e.g. if it is a root node), the event is immediately
 * emitted.
 * <p>
 * This is an immutable object.
 */
public class NodeOnlineEvent extends AbstractNodeEvent {
    /**
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public NodeOnlineEvent(final Node node) {
        super(node);
    }

    @Override
    public String toString() {
        return "NodeOnlineEvent{" +
                "node=" + node +
                '}';
    }
}