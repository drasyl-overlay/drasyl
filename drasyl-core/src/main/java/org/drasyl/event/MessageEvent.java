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
 * This event signals that the node has received a message addressed to it.
 * <p>
 * This is an immutable object.
 */
public class MessageEvent implements Event {
    private final CompressedPublicKey sender;
    private final Object payload;

    /**
     * Creates a new {@code MessageEvent}
     *
     * @param sender  the message's sender
     * @param payload content of the message
     * @throws NullPointerException if {@code sender} or {@code payload} is {@code null}
     */
    public MessageEvent(final CompressedPublicKey sender, final Object payload) {
        this.sender = requireNonNull(sender);
        this.payload = requireNonNull(payload);
    }

    /**
     * Returns the message's sender.
     *
     * @return the message's sender
     */
    public CompressedPublicKey getSender() {
        return sender;
    }

    /**
     * Returns the message's payload.
     *
     * @return the message's payload
     */
    public Object getPayload() {
        return payload;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sender, payload);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MessageEvent that = (MessageEvent) o;
        return Objects.equals(sender, that.sender) &&
                Objects.deepEquals(payload, that.payload);
    }

    @Override
    public String toString() {
        return "MessageEvent{" +
                "sender=" + sender +
                ", message=" + payload +
                '}';
    }
}