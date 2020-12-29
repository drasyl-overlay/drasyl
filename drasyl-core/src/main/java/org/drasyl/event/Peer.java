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

import org.drasyl.identity.CompressedPublicKey;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Used by {@link Event} to describe an event related to a Peer (e.g. {@link PeerRelayEvent}, {@link
 * PeerDirectEvent}).
 * <p>
 * This is an immutable object.
 */
public class Peer {
    private final CompressedPublicKey publicKey;

    Peer(final CompressedPublicKey publicKey) {
        this.publicKey = requireNonNull(publicKey);
    }

    /**
     * Returns the peer's public key.
     *
     * @return the peer's public key.
     */
    public CompressedPublicKey getPublicKey() {
        return publicKey;
    }

    @Override
    public int hashCode() {
        return Objects.hash(publicKey);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Peer peer = (Peer) o;
        return Objects.equals(publicKey, peer.publicKey);
    }

    @Override
    public String toString() {
        return "Peer{" +
                "publicKey=" + publicKey +
                '}';
    }

    /**
     * @throws NullPointerException if {@code publicKey} is {@code null}
     */
    public static Peer of(final CompressedPublicKey publicKey) {
        return new Peer(publicKey);
    }
}