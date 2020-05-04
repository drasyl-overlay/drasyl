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
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.security.KeyPair;
import java.util.Set;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class JoinTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private CompressedPublicKey publicKey;
    private Set<URI> endpoints;

    @BeforeEach
    void setUp() throws CryptoException {
        UserAgentMessage.userAgentGenerator = () -> "";
        KeyPair keyPair = Crypto.generateKeys();
        publicKey = CompressedPublicKey.of(keyPair.getPublic());
        endpoints = Set.of(URI.create("ws://test"));
    }

    @AfterEach
    public void tearDown() {
        UserAgentMessage.userAgentGenerator = UserAgentMessage.defaultUserAgentGenerator;
    }

    @Test
    public void toJson() throws JsonProcessingException {
        Join message = new Join(publicKey, endpoints);

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"type\":\"Join\",\"messageID\":\"" + message.getMessageID() + "\",\"userAgent\":\"\",\"publicKey\":\"" + publicKey.getCompressedKey() + "\",\"endpoints\":[\"ws://test\"],\"signature\":null}");

        // Ignore toString()
        message.toString();
    }

    @Test
    public void fromJson() throws IOException {
        String json = "{\"type\":\"Join\",\"messageID\":\"4AE5CDCD8C21719F8E779F21\",\"userAgent\":\"\",\"publicKey\":\"" + publicKey.getCompressedKey() + "\",\"endpoints\":[\"ws://test\"],\"signature\":null}";

        assertThat(JSON_MAPPER.readValue(json, IMessage.class), instanceOf(Join.class));
    }

    @Test
    public void nullTest() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new Join(null, endpoints);
        }, "Join requires a public key");

        Assertions.assertThrows(NullPointerException.class, () -> {
            new Join(publicKey, null);
        }, "Join requires endpoints");

        Assertions.assertThrows(NullPointerException.class, () -> {
            new Join(null, null);
        }, "Join requires a public key and endpoints");
    }

    @Test
    void testEquals() throws CryptoException {
        Join message1 = new Join(publicKey, endpoints);
        Join message2 = new Join(publicKey, endpoints);
        Join message3 = new Join(CompressedPublicKey.of(Crypto.generateKeys().getPublic()), Set.of());

        assertEquals(message1, message2);
        assertNotEquals(message2, message3);
    }

    @Test
    void testHashCode() throws CryptoException {
        Join message1 = new Join(publicKey, endpoints);
        Join message2 = new Join(publicKey, endpoints);
        Join message3 = new Join(CompressedPublicKey.of(Crypto.generateKeys().getPublic()), Set.of());

        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotEquals(message2.hashCode(), message3.hashCode());
    }
}