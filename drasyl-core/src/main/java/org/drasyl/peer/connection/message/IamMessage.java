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
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.identity.CompressedPublicKey;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * This message is used as response to a {@link WhoAreYouMessage} and contains the public key of
 * this peer.
 * <p>
 * This is an immutable object.
 */
public class IamMessage extends AbstractMessage implements ResponseMessage<WhoAreYouMessage> {
    private final CompressedPublicKey publicKey;
    private final MessageId correspondingId;

    @JsonCreator
    public IamMessage(@JsonProperty("id") final MessageId id,
                      @JsonProperty("publicKey") final CompressedPublicKey publicKey,
                      @JsonProperty("correspondingId") final MessageId correspondingId) {
        super(id);
        this.publicKey = requireNonNull(publicKey);
        this.correspondingId = requireNonNull(correspondingId);
    }

    public IamMessage(final CompressedPublicKey publicKey, final MessageId correspondingId) {
        this.publicKey = requireNonNull(publicKey);
        this.correspondingId = requireNonNull(correspondingId);
    }

    @Override
    public MessageId getCorrespondingId() {
        return correspondingId;
    }

    public CompressedPublicKey getPublicKey() {
        return publicKey;
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
        final IamMessage that = (IamMessage) o;
        return Objects.equals(publicKey, that.publicKey) &&
                Objects.equals(correspondingId, that.correspondingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), publicKey, correspondingId);
    }

    @Override
    public String toString() {
        return "IamMessage{" +
                "publicKey=" + publicKey +
                ", correspondingId='" + correspondingId + '\'' +
                ", id='" + id + '\'' +
                "} ";
    }
}