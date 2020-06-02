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
package org.drasyl.peer.connection;

import org.drasyl.DrasylNode;
import org.drasyl.event.Event;
import org.drasyl.event.EventType;
import org.drasyl.identity.Address;
import org.drasyl.peer.connection.message.AbstractMessageWithUserAgent;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.util.Pair;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The {@link LoopbackPeerConnection} object models an loobback connection of this node.
 */
@SuppressWarnings({ "java:S2160" })
public class LoopbackPeerConnection extends PeerConnection {
    private final Consumer<Event> onEvent;
    protected CompletableFuture<Boolean> closedCompletable;
    protected AtomicBoolean isClosed;

    /**
     * Creates a new LoopbackPeerConnection connection of this node.
     *
     * @param onEvent            reference to {@link DrasylNode#onEvent(Event)}
     * @param address           reference to {@link Address}
     * @param connectionsManager reference to the {@link ConnectionsManager}
     */
    public LoopbackPeerConnection(Consumer<Event> onEvent,
                                  Address address,
                                  ConnectionsManager connectionsManager) {
        this(onEvent, address, new CompletableFuture<>(), new AtomicBoolean(false), connectionsManager);
    }

    LoopbackPeerConnection(Consumer<Event> onEvent,
                           Address address,
                           CompletableFuture<Boolean> closedCompletable,
                           AtomicBoolean isClosed, ConnectionsManager connectionsManager) {
        super(address, connectionsManager);
        this.onEvent = onEvent;
        this.closedCompletable = closedCompletable;
        this.isClosed = isClosed;
    }

    @Override
    protected void close(CloseReason reason) {
        if (isClosed.compareAndSet(false, true)) {
            closedCompletable.complete(true);
        }
    }

    @Override
    public void send(Message message) {
        if (isClosed.get()) {
            return;
        }

        if (!(message instanceof ApplicationMessage)) {
            throw new IllegalArgumentException("LoopbackPeerConnection can only handle ApplicationMessage's.");
        }

        ApplicationMessage applicationMessage = (ApplicationMessage) message;
        onEvent.accept(new Event(EventType.EVENT_MESSAGE, Pair.of(applicationMessage.getSender(), applicationMessage.getPayload())));
    }

    @Override
    public String getUserAgent() {
        return AbstractMessageWithUserAgent.userAgentGenerator.get();
    }

    @Override
    public CompletableFuture<Boolean> isClosed() {
        return closedCompletable;
    }

    @Override
    public String toString() {
        return "LoopbackPeerConnection{}";
    }
}
