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
package org.drasyl.identity;

import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.drasyl.util.JSONUtil.JACKSON_READER;
import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CompressedKeyPairTest {
    @Nested
    class JsonDeserialization {
        @Test
        void shouldDeserializeToCorrectObject() throws IOException {
            final String json = "{\n" +
                    "  \"publicKey\": \"AnduU7DI6cLHCLZ0x5KebE5EXE+XpAdwAstnnE3QhXYJ\",\n" +
                    "  \"privateKey\": \"B5Cf44xUUxCYBd5AwkqRpfPi3kjxVLddhpSSfzyATzY=\"\n" +
                    "}";

            assertEquals(
                    CompressedKeyPair.of("02776e53b0c8e9c2c708b674c7929e6c4e445c4f97a4077002cb679c4dd0857609", "07909fe38c5453109805de40c24a91a5f3e2de48f154b75d8694927f3c804f36"),
                    JACKSON_READER.readValue(json, CompressedKeyPair.class)
            );
        }
    }

    @Nested
    class JsonSerialization {
        @Test
        void shouldSerializeToCorrectJson() throws IOException {
            final CompressedKeyPair keyPair = CompressedKeyPair.of("02776e53b0c8e9c2c708b674c7929e6c4e445c4f97a4077002cb679c4dd0857609", "07909fe38c5453109805de40c24a91a5f3e2de48f154b75d8694927f3c804f36");

            assertThatJson(JACKSON_WRITER.writeValueAsString(keyPair))
                    .when(Option.IGNORING_ARRAY_ORDER)
                    .isEqualTo("{\n" +
                            "  \"publicKey\": \"AnduU7DI6cLHCLZ0x5KebE5EXE+XpAdwAstnnE3QhXYJ\",\n" +
                            "  \"privateKey\": \"B5Cf44xUUxCYBd5AwkqRpfPi3kjxVLddhpSSfzyATzY=\"\n" +
                            "}");
        }
    }
}
