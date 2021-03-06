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
package org.drasyl.event;

/**
 * This events signals that the node has terminated normally.
 * <p>
 * This is an immutable object.
 */
public class NodeNormalTerminationEvent extends AbstractNodeEvent {
    /**
     * @throws NullPointerException if {@code node} is {@code null}
     * @deprecated Use {@link #of(Node)} instead.
     */
    // make method private on next release
    @Deprecated(since = "0.4.0", forRemoval = true)
    public NodeNormalTerminationEvent(final Node node) {
        super(node);
    }

    @Override
    public String toString() {
        return "NodeNormalTerminationEvent{" +
                "node=" + node +
                '}';
    }

    /**
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public static NodeNormalTerminationEvent of(final Node node) {
        return new NodeNormalTerminationEvent(node);
    }
}
