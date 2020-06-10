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
package org.drasyl.peer.connection.message;

import org.drasyl.identity.CompressedPublicKey;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

public abstract class AbstractGrandchildMessage extends AbstractMessage implements RequestMessage {
    protected final CompressedPublicKey publicKey;
    protected final Set<URI> endpoints;

    AbstractGrandchildMessage(CompressedPublicKey publicKey, Set<URI> endpoints) {
        this.publicKey = publicKey;
        this.endpoints = endpoints;
    }

    public CompressedPublicKey getPublicKey() {
        return publicKey;
    }

    public Set<URI> getEndpoints() {
        return this.endpoints;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), publicKey, endpoints);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        AbstractGrandchildMessage that = (AbstractGrandchildMessage) o;
        return Objects.equals(publicKey, that.publicKey) &&
                Objects.equals(endpoints, that.endpoints);
    }
}