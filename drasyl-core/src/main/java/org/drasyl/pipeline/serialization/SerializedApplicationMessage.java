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
package org.drasyl.pipeline.serialization;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;

import java.util.Arrays;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A message from or to the application whose content has been serialized to a byte array so that
 * the message can be delivered to remote nodes.
 */
public class SerializedApplicationMessage extends DefaultAddressedEnvelope<CompressedPublicKey, byte[]> {
    private final String type;

    public SerializedApplicationMessage(final CompressedPublicKey sender,
                                        final CompressedPublicKey recipient,
                                        final Class<?> type,
                                        final byte[] content) {
        this(sender, recipient, type.getName(), content);
    }

    public SerializedApplicationMessage(final CompressedPublicKey sender,
                                        final CompressedPublicKey recipient,
                                        final String type,
                                        final byte[] content) {
        super(sender, recipient, content);
        this.type = requireNonNull(type);
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
        final SerializedApplicationMessage that = (SerializedApplicationMessage) o;
        return Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSender(), getRecipient(), Arrays.hashCode(getContent()), type);
    }

    @Override
    public String toString() {
        return "SerializedApplicationMessage{" +
                "sender='" + getSender() + "'," +
                "recipient='" + getRecipient() + "'," +
                "type='" + type + "'," +
                "content=byte[" + getContent().length + "]" +
                '}';
    }

    public Class<?> getTypeClazz() throws ClassNotFoundException {
        return Class.forName(type);
    }

    public String getType() {
        return type;
    }
}
