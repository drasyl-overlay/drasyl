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

import org.drasyl.identity.Identity;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Used by {@link Event} to describe an event related to the local Node (e.g. {@link NodeUpEvent},
 * {@link NodeOnlineEvent}).
 * <p>
 * This is an immutable object.
 */
public class Node {
    private final Identity identity;
    private final int port;

    Node(final Identity identity, final int port) {
        this.identity = requireNonNull(identity);
        if (port < 0) {
            throw new IllegalArgumentException("port must be non-negative.");
        }
        this.port = port;
    }

    @Override
    public int hashCode() {
        return Objects.hash(identity, port);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Node node = (Node) o;
        return Objects.equals(identity, node.identity) &&
                port == port;
    }

    @Override
    public String toString() {
        return "Node{" +
                "identity=" + identity +
                ", port=" + port +
                '}';
    }

    /**
     * Returns the node's identity.
     *
     * @return the node's identity
     */
    public Identity getIdentity() {
        return identity;
    }

    /**
     * Returns the node's server port.
     *
     * @return the node's server port
     */
    public int getPort() {
        return port;
    }

    /**
     * @throws NullPointerException if {@code identity} is {@code null}
     */
    public static Node of(final Identity identity) {
        return of(identity, 0);
    }

    /**
     * @throws NullPointerException     if {@code identity} is {@code null}
     * @throws IllegalArgumentException if {@code port} is negative
     */
    public static Node of(final Identity identity, final int port) {
        return new Node(identity, port);
    }
}