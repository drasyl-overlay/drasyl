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
 * This event signals that communication with this peer is only possible by relaying messages via a
 * super peer. If there is no connection to a super peer, no communication with this peer is
 * possible.
 * <p>
 * This is an immutable object.
 *
 * @see PeerDirectEvent
 * @see NodeOnlineEvent
 * @see NodeOfflineEvent
 */
public class PeerRelayEvent extends AbstractPeerEvent {
    private PeerRelayEvent(final Peer peer) {
        super(peer);
    }

    @Override
    public String toString() {
        return "PeerRelayEvent{" +
                "peer=" + peer +
                '}';
    }

    /**
     * @throws NullPointerException if {@code peer} is {@code null}
     */
    public static PeerRelayEvent of(final Peer peer) {
        return new PeerRelayEvent(peer);
    }
}
