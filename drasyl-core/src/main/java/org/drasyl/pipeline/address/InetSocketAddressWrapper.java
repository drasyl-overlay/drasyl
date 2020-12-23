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
package org.drasyl.pipeline.address;

import org.drasyl.pipeline.Pipeline;

import java.net.InetSocketAddress;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * This class holds an {@link InetSocketAddress} for the processing inside the {@link Pipeline} as
 * an {@link Address}.
 */
public class InetSocketAddressWrapper implements Address {
    private final InetSocketAddress address;

    public InetSocketAddressWrapper(final InetSocketAddress address) {
        this.address = requireNonNull(address);
    }

    public static InetSocketAddressWrapper of(final InetSocketAddress address) {
        return new InetSocketAddressWrapper(address);
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    @Override
    public String toString() {
        return address.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final InetSocketAddressWrapper that = (InetSocketAddressWrapper) o;
        return Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }
}
