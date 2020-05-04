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

package org.drasyl.core.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.jsonunit.core.Option;
import org.drasyl.crypto.Crypto;
import org.drasyl.crypto.CryptoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.KeyPair;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class CompressedPrivateKeyTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private KeyPair keyPair;

    @BeforeEach
    void setUp() {
        keyPair = Crypto.generateKeys();
    }

    @Test
    public void ofTest() throws CryptoException {
        CompressedPrivateKey compressedPrivateKey1 = CompressedPrivateKey.of(keyPair.getPrivate());
        CompressedPrivateKey compressedPrivateKey2 = CompressedPrivateKey.of(compressedPrivateKey1.getCompressedKey());
        CompressedPrivateKey compressedPrivateKey3 = CompressedPrivateKey.of(compressedPrivateKey2.toPrivKey());

        assertEquals(compressedPrivateKey1, compressedPrivateKey2);
        assertEquals(compressedPrivateKey1, compressedPrivateKey3);
        assertEquals(compressedPrivateKey2, compressedPrivateKey3);
        assertEquals(compressedPrivateKey1.hashCode(), compressedPrivateKey2.hashCode());
        assertEquals(compressedPrivateKey1.hashCode(), compressedPrivateKey3.hashCode());
        assertEquals(compressedPrivateKey2.hashCode(), compressedPrivateKey3.hashCode());
    }

    @Test
    public void toJson() throws IOException, CryptoException {
        CompressedPrivateKey compressedPrivateKey = CompressedPrivateKey.of(keyPair.getPrivate());

        assertThatJson(JSON_MAPPER.writeValueAsString(compressedPrivateKey))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo(compressedPrivateKey.toString());

        // Ignore toString()
        compressedPrivateKey.toString();
        assertEquals(compressedPrivateKey, JSON_MAPPER.readValue(JSON_MAPPER.writeValueAsString(compressedPrivateKey), CompressedPrivateKey.class));
    }

    @Test
    public void fromJson() throws IOException {
        String json = "\"045ADCB39AA39A81E8C95A0E309B448FA60A41535B3F3CA41AA2745558DFFD6B\"";

        assertThat(JSON_MAPPER.readValue(json, CompressedPrivateKey.class), instanceOf(CompressedPrivateKey.class));
    }
}