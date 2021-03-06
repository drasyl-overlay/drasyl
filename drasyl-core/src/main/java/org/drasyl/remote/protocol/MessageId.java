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
package org.drasyl.remote.protocol;

import org.drasyl.annotation.NonNull;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.HexUtil;
import org.drasyl.pipeline.message.AddressedEnvelope;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * A {@link AddressedEnvelope} is uniquely identified by its {@link #MESSAGE_ID_LENGTH} bytes
 * identifier.
 * <p>
 * This is an immutable object.
 */
public final class MessageId {
    public static final int MESSAGE_ID_LENGTH = 8;
    private final byte[] id;

    private MessageId(@NonNull final byte[] id) {
        if (!isValidMessageId(id)) {
            throw new IllegalArgumentException("ID must be a " + MESSAGE_ID_LENGTH + " bit byte array: " + HexUtil.bytesToHex(id));
        }
        this.id = id.clone();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(id);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MessageId messageId = (MessageId) o;
        return Arrays.equals(id, messageId.id);
    }

    @Override
    public String toString() {
        return HexUtil.bytesToHex(id);
    }

    public byte[] byteArrayValue() {
        return id.clone();
    }

    public long longValue() {
        return ByteBuffer.wrap(id).getLong();
    }

    /**
     * Static factory to retrieve a randomly generated {@link MessageId}.
     *
     * @return A randomly generated {@code MessageId}
     */
    public static MessageId randomMessageId() {
        return new MessageId(Crypto.randomBytes(MESSAGE_ID_LENGTH));
    }

    /**
     * Checks if {@code id} is a valid identifier.
     *
     * @param id string to be validated
     * @return {@code true} if valid. Otherwise {@code false}
     */
    public static boolean isValidMessageId(final byte[] id) {
        return id != null && id.length == MESSAGE_ID_LENGTH;
    }

    /**
     * @throws NullPointerException if {@code id} is {@code null}
     */
    public static MessageId of(@NonNull final byte[] id) {
        return new MessageId(id);
    }

    /**
     * @throws NullPointerException if {@code id} is {@code null}
     */
    public static MessageId of(@NonNull final String id) {
        return new MessageId(HexUtil.parseHexBinary(id));
    }

    public static MessageId of(final long id) {
        return of(ByteBuffer.allocate(Long.BYTES).putLong(id).array());
    }
}
