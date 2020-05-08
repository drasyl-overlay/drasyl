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
package org.drasyl.core.node.connections;

import io.netty.channel.Channel;
import io.reactivex.rxjava3.core.SingleEmitter;
import org.drasyl.core.common.messages.IMessage;
import org.drasyl.core.common.models.Pair;
import org.drasyl.core.node.identity.Identity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The {@link ClientConnection} object models the clients of a drasyl node server.
 */
@SuppressWarnings({ "squid:S00107" })
public class ClientConnection extends NettyPeerConnection {
    private static final Logger LOG = LoggerFactory.getLogger(ClientConnection.class);

    /**
     * Creates a new connection with an unknown User-Agent.
     *
     * @param channel  channel of the connection
     * @param endpoint the URI of the target system
     * @param identity the identity of this {@link ClientConnection}
     */
    public ClientConnection(Channel channel, URI endpoint, Identity identity) {
        super(channel, endpoint, identity);
    }

    /**
     * Creates a new connection.
     *
     * @param channel   channel of the connection
     * @param endpoint  the URI of the target system
     * @param identity  the identity of this {@link ClientConnection}
     * @param userAgent the User-Agent string
     */
    public ClientConnection(Channel channel, URI endpoint, Identity identity, String userAgent) {
        super(channel, endpoint, identity, userAgent);
    }

    protected ClientConnection(Channel myChannel,
                               String userAgent,
                               Identity identity,
                               URI endpoint,
                               AtomicBoolean isClosed,
                               ConcurrentHashMap<String, Pair<Class<? extends IMessage>, SingleEmitter<IMessage>>> emitters,
                               CompletableFuture<Boolean> closedCompletable) {
        super(myChannel, userAgent, identity, endpoint, isClosed, emitters, closedCompletable);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ClientConnection that = (ClientConnection) o;
        return Objects.equals(identity, that.identity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identity);
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
}
