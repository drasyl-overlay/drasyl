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
package org.drasyl.core.common.messages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.jsonunit.core.Option;
import org.drasyl.core.models.CompressedPublicKey;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.node.identity.IdentityTestHelper;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.KeyPair;
import java.util.Base64;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MessageTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    KeyPair keyPair;
    CompressedPublicKey senderPubKey;
    Identity sender;
    Identity recipient;
    Message message;

    @BeforeEach
    void setUp() throws CryptoException {
        keyPair = Crypto.generateKeys();
        senderPubKey = CompressedPublicKey.of(keyPair.getPublic());
        sender = Identity.of(senderPubKey);
        recipient = IdentityTestHelper.random();
        message = new Message(sender, recipient, new byte[]{ 0x00, 0x01, 0x02 });
        Crypto.sign(keyPair.getPrivate(), message);
    }

    @Test
    public void toJson() throws JsonProcessingException {
        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"type\":\"Message\",\"messageID\":\"" + message.getMessageID() + "\",\"sender\":\"" + sender.getId() + "\",\"recipient\":\"" + recipient.getId() + "\",\"payload\":\"AAEC\",\"signature\":{\"bytes\":\"" + new String(Base64.getEncoder().encode(message.getSignature().getBytes())) + "\"}}");

        System.out.println(JSON_MAPPER.writeValueAsString(message));

        // Ignore toString()
        message.toString();
    }

    @Test
    public void fromJson() throws IOException {
        String json = "{\"type\":\"Message\",\"messageID\":\"" + message.getMessageID() + "\",\"sender\":\"" + sender.getId() + "\",\"recipient\":\"" + recipient.getId() + "\",\"payload\":\"AAEC\",\"signature\":{\"bytes\":\"" + new String(Base64.getEncoder().encode(message.getSignature().getBytes())) + "\"}}";

        Message msg = JSON_MAPPER.readValue(json, Message.class);

        assertThat(msg, instanceOf(Message.class));
        assertTrue(Crypto.verifySignature(keyPair.getPublic(), msg));
    }

    @Test
    public void nullTest() {
        assertThrows(NullPointerException.class, () -> {
            new Message(null, recipient, new byte[]{});
        }, "Message requires a sender");

        assertThrows(NullPointerException.class, () -> {
            new Message(sender, null, new byte[]{});
        }, "Message requires a recipient");

        assertThrows(NullPointerException.class, () -> {
            new Message(sender, recipient, null);
        }, "Message requires a payload");

        assertThrows(NullPointerException.class, () -> {
            new Message(null, null, null);
        }, "Message requires a sender, a recipient and a payload");
    }

    @Test
    void testNotEqualsBecauseOfDifferentIds() throws CryptoException {
        Message message1 = new Message(sender, recipient, new byte[]{ 0x00, 0x01, 0x02 });
        Message message2 = new Message(sender, recipient, new byte[]{ 0x00, 0x01, 0x02 });
        Message message3 = new Message(sender, recipient, new byte[]{ 0x03, 0x02, 0x01 });

        Crypto.sign(keyPair.getPrivate(), message1);
        Crypto.sign(keyPair.getPrivate(), message2);
        Crypto.sign(keyPair.getPrivate(), message3);

        assertNotEquals(message1, message2);
        assertNotEquals(message2, message3);
    }

    @Test
    void testHashCode() throws CryptoException {
        Message message1 = new Message(sender, recipient, new byte[]{ 0x00, 0x01, 0x02 });
        Message message2 = new Message(sender, recipient, new byte[]{ 0x00, 0x01, 0x02 });
        Message message3 = new Message(sender, recipient, new byte[]{ 0x03, 0x02, 0x01 });

        Crypto.sign(keyPair.getPrivate(), message1);
        Crypto.sign(keyPair.getPrivate(), message2);
        Crypto.sign(keyPair.getPrivate(), message3);

        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotEquals(message2.hashCode(), message3.hashCode());
    }
}
