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

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * This events signals that the node encountered an unrecoverable error.
 * <p>
 * This is an immutable object.
 */
public class NodeUnrecoverableErrorEvent extends AbstractNodeEvent {
    private final Throwable error;

    /**
     * @throws NullPointerException if {@code node} or {@code error} is {@code null}
     */
    public NodeUnrecoverableErrorEvent(final Node node, final Throwable error) {
        super(node);
        this.error = requireNonNull(error);
    }

    /**
     * Returns the exception that crashed the node.
     *
     * @return the exception that crashed the node
     */
    public Throwable getError() {
        return error;
    }

    @Override
    public String toString() {
        return "NodeUnrecoverableErrorEvent{" +
                "error=" + error +
                ", node=" + node +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), error);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final NodeUnrecoverableErrorEvent that = (NodeUnrecoverableErrorEvent) o;
        return Objects.equals(error, that.error);
    }
}