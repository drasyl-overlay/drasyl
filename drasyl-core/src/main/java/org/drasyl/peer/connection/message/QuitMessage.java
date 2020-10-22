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
import com.fasterxml.jackson.annotation.JsonValue;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A message representing a termination of a connection.
 * <p>
 * This is an immutable object.
 */
public class QuitMessage extends AbstractMessage implements RequestMessage, AddressableMessage {
    private final CompressedPublicKey sender;
    private final ProofOfWork proofOfWork;
    private final CompressedPublicKey recipient;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final CloseReason reason;

    @JsonCreator
    private QuitMessage(@JsonProperty("id") final MessageId id,
                        @JsonProperty("sender") final CompressedPublicKey sender,
                        @JsonProperty("proofOfWork") final ProofOfWork proofOfWork,
                        @JsonProperty("recipient") final CompressedPublicKey recipient,
                        @JsonProperty("reason") final CloseReason reason) {
        super(id);
        this.sender = requireNonNull(sender);
        this.proofOfWork = requireNonNull(proofOfWork);
        this.recipient = requireNonNull(recipient);
        this.reason = requireNonNull(reason);
    }

    public QuitMessage(final CompressedPublicKey sender,
                       final ProofOfWork proofOfWork,
                       final CompressedPublicKey recipient,
                       final CloseReason reason) {
        this.sender = requireNonNull(sender);
        this.proofOfWork = requireNonNull(proofOfWork);
        this.recipient = requireNonNull(recipient);
        this.reason = requireNonNull(reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sender, proofOfWork, recipient, reason);
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
        final QuitMessage that = (QuitMessage) o;
        return Objects.equals(sender, that.sender) &&
                Objects.equals(proofOfWork, that.proofOfWork) &&
                Objects.equals(recipient, that.recipient) &&
                reason == that.reason;
    }

    @Override
    public String toString() {
        return "QuitMessage{" +
                "sender='" + sender + '\'' +
                ", proofOfWork='" + proofOfWork + '\'' +
                ", recipient='" + recipient + '\'' +
                ", reason='" + reason + '\'' +
                ", id='" + id +
                '}';
    }

    @Override
    public CompressedPublicKey getSender() {
        return sender;
    }

    @Override
    public ProofOfWork getProofOfWork() {
        return proofOfWork;
    }

    @Override
    public CompressedPublicKey getRecipient() {
        return recipient;
    }

    public CloseReason getReason() {
        return reason;
    }

    /**
     * Specifies the reason for closing the connection.
     */
    public enum CloseReason {
        REASON_NEW_SESSION("New Connection with this Identity has been created."),
        REASON_SHUTTING_DOWN("Peer is shutting down.");
        private static final Map<String, CloseReason> reasons = new HashMap<>();

        static {
            for (final CloseReason description : values()) {
                reasons.put(description.getDescription(), description);
            }
        }

        private final String description;

        CloseReason(final String description) {
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
        public static CloseReason from(final String description) {
            return reasons.get(description);
        }
    }
}