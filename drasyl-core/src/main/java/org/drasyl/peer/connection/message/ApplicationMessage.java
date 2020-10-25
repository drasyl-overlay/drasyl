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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.pipeline.codec.ObjectHolder;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * A message that is sent by an application running on drasyl.
 * <p>
 * This is an immutable object.
 */
public class ApplicationMessage extends RelayableMessage implements RequestMessage {
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    protected final Map<String, String> headers;
    protected final byte[] payload;

    @JsonCreator
    private ApplicationMessage(@JsonProperty("id") final MessageId id,
                               @JsonProperty("userAgent") final String userAgent,
                               @JsonProperty("sender") final CompressedPublicKey sender,
                               @JsonProperty("proofOfWork") final ProofOfWork proofOfWork,
                               @JsonProperty("recipient") final CompressedPublicKey recipient,
                               @JsonProperty("headers") final Map<String, String> headers,
                               @JsonProperty("payload") final byte[] payload,
                               @JsonProperty("hopCount") final short hopCount) {
        super(id, userAgent, sender, proofOfWork, recipient, hopCount);
        if (headers != null) {
            this.headers = Map.copyOf(headers);
        }
        else {
            // needed for backward compatibility
            this.headers = Map.of();
        }
        this.payload = requireNonNull(payload);
    }

    public ApplicationMessage(final MessageId id,
                              final CompressedPublicKey sender,
                              final ProofOfWork proofOfWork,
                              final CompressedPublicKey recipient,
                              final byte[] payload,
                              final short hopCount) {
        super(id, sender, proofOfWork, recipient, hopCount);
        this.headers = Map.of();
        this.payload = requireNonNull(payload);
    }

    /**
     * Creates a new message.
     *
     * @param sender      The sender
     * @param proofOfWork The sender's proof of work
     * @param recipient   The recipient
     * @param payload     The data to be sent
     */
    public ApplicationMessage(final CompressedPublicKey sender,
                              final ProofOfWork proofOfWork,
                              final CompressedPublicKey recipient,
                              final Map<String, String> headers,
                              final byte[] payload) {
        this(sender, proofOfWork, recipient, headers, payload, (short) 0);
    }

    ApplicationMessage(final CompressedPublicKey sender,
                       final ProofOfWork proofOfWork,
                       final CompressedPublicKey recipient,
                       final Map<String, String> headers,
                       final byte[] payload,
                       final short hopCount) {
        super(sender, proofOfWork, recipient, hopCount);
        this.headers = requireNonNull(headers);
        this.payload = requireNonNull(payload);
    }

    /**
     * Creates a new message.
     *
     * @param sender      The sender
     * @param proofOfWork The sender's proof of work
     * @param recipient   The recipient
     * @param payload     The data to be sent
     */
    public ApplicationMessage(final CompressedPublicKey sender,
                              final ProofOfWork proofOfWork,
                              final CompressedPublicKey recipient,
                              final byte[] payload) {
        this(sender, proofOfWork, recipient, Map.of(), payload, (short) 0);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Returns the value of header with name <code>name</code>, or {@code null} if this header does
     * not exist. Returns {@code byte[].class.getName()} if the header does not contain a value for
     * {@code name = } {@link ObjectHolder#CLASS_KEY_NAME}.
     *
     * @return value of header with name <code>name</code>, or {@code null} if this header does not
     * exist
     */
    public String getHeader(final String name) {
        // for backward compatibility
        if (name.equals(ObjectHolder.CLASS_KEY_NAME) && !headers.containsKey(name)) {
            return byte[].class.getName();
        }

        return headers.get(name);
    }

    public byte[] getPayload() {
        return payload;
    }

    /**
     * @return a ByteBuf that wraps the underling payload byte array
     */
    public ByteBuf payloadAsByteBuf() {
        return Unpooled.wrappedBuffer(payload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(super.hashCode(), headers);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
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
        final ApplicationMessage that = (ApplicationMessage) o;
        return Arrays.equals(payload, that.payload) &&
                Objects.equals(headers, that.headers);
    }

    @Override
    public String toString() {
        return "ApplicationMessage{" +
                "sender=" + sender +
                ", proofOfWork=" + proofOfWork +
                ", recipient=" + recipient +
                ", headers=" + headers +
                ", payload=byte[" + Optional.ofNullable(payload).orElse(new byte[]{}).length + "] { ... }" +
                ", hopCount=" + hopCount +
                ", id='" + id + '\'' +
                '}';
    }
}