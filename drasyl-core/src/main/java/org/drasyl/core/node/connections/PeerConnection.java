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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.reactivex.rxjava3.core.Single;
import org.drasyl.core.common.message.Message;
import org.drasyl.core.common.message.RequestMessage;
import org.drasyl.core.common.message.ResponseMessage;
import org.drasyl.core.node.ConnectionsManager;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.crypto.Crypto;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link PeerConnection} object represents a connection to another peer, e.g. local or remote.
 * For this purpose, this object provides a standardized interface so that the actual connection
 * type is abstracted and the same operations are always available.
 */
public abstract class PeerConnection {
    protected final String connectionId = Crypto.randomString(8);
    protected Identity identity;

    public PeerConnection(Identity identity,
                          ConnectionsManager connectionsManager) {
        this.identity = identity;
        connectionsManager.addConnection(this, this::close);
    }

    /**
     * @return Returns the unique id of this connection.
     */
    public String getConnectionId() {
        return this.connectionId;
    }

    /**
     * Causes the {@link PeerConnection} to close. All pending messages are still processed, but no
     * new messages can be sent after this method has been called.
     *
     * @param reason reason why this connection is closed
     */
    protected abstract void close(CloseReason reason);

    /**
     * Sends a message to the peer without waiting for any response.
     *
     * @param message message that should be sent
     */
    public abstract void send(Message<?> message);

    /**
     * Sends a message to the peer and returns a {@link Single} object for potential responses to
     * this message.
     *
     * @param message       message that should be sent
     * @param responseClass the class of the response object, to avoid * ClassCastExceptions
     * @param <T>           the type of the response
     * @return a {@link Single} object that can be fulfilled with a {@link Message response} to the
     * * message
     */
    public abstract <T extends ResponseMessage<? extends RequestMessage<?>, ? extends Message<?>>> Single<T> send(
            RequestMessage<?> message,
            Class<T> responseClass);

    /**
     * Sets the result of a {@link Single} object from a {@link #send(RequestMessage, Class)} call.
     *
     * @param response the response
     */
    public abstract void setResponse(ResponseMessage<? extends RequestMessage<?>, ? extends Message<?>> response);

    /**
     * Returns the User-Agent string.
     */
    public abstract String getUserAgent();

    /**
     * Returns the endpoint of this connection.
     */
    public abstract URI getEndpoint();

    /**
     * Returns the identity of the peer.
     */
    public Identity getIdentity() {
        return identity;
    }

    /**
     * This {@link CompletableFuture} becomes complete as soon as this connection has been closed
     * successfully.
     */
    public abstract CompletableFuture<Boolean> isClosed();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PeerConnection that = (PeerConnection) o;
        return Objects.equals(connectionId, that.connectionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connectionId);
    }

    /**
     * Specifies the reason for closing the {@link PeerConnection}.
     */
    public enum CloseReason {
        REASON_NEW_SESSION("New Connection with this Identity has been created."),
        REASON_SHUTTING_DOWN("Server is shutting down."),
        REASON_INTERNAL_REJECTION("Server rejects or closed this Connection of internal reasons.");
        private static final Map<String, CloseReason> reasons = new HashMap<>();

        static {
            for (CloseReason code : values()) {
                reasons.put(code.getDescription(), code);
            }
        }

        private final String description;

        CloseReason(String description) {
            this.description = description;
        }

        /**
         * @return a human readable representation of the reason.
         */
        @JsonValue
        public String getDescription() {
            return description;
        }

        @JsonCreator
        public static CloseReason from(String description) {
            return reasons.get(description);
        }
    }
}