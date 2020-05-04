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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class NodeServerExceptionTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    public void toJson() throws JsonProcessingException {
        NodeServerException message = new NodeServerException("something horrible has happened");

        assertThatJson(JSON_MAPPER.writeValueAsString(message))
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("{\"type\":\"NodeServerException\",\"messageID\":\"" + message.getMessageID() + "\",\"exception\":\"something horrible has happened\",\"signature\":null}");

        // Ignore toString()
        message.toString();
    }

    @Test
    public void fromJson() throws IOException {
        String json = "{\"type\":\"NodeServerException\",\"messageID\":\"77175D7235920F3BA17341D7\"," +
                "\"exception\":\"something horrible has happened\",\"signature\":null}";

        assertThat(JSON_MAPPER.readValue(json, IMessage.class), instanceOf(NodeServerException.class));
    }

    @Test
    public void nullTest() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new NodeServerException((String) null);
        }, "NodeServerException requires an exception");

        Assertions.assertThrows(NullPointerException.class, () -> {
            new NodeServerException((Exception) null);
        }, "NodeServerException requires an exception");
    }

    @Test
    void testEquals() {
        NodeServerException message1 = new NodeServerException("something horrible has happened");
        NodeServerException message2 = new NodeServerException("something horrible has happened");
        NodeServerException message3 = new NodeServerException("something dreadful has happened");

        assertEquals(message1, message2);
        assertNotEquals(message2, message3);
    }

    @Test
    void testHashCode() {
        NodeServerException message1 = new NodeServerException("something horrible has happened");
        NodeServerException message2 = new NodeServerException("something horrible has happened");
        NodeServerException message3 = new NodeServerException("something dreadful has happened");

        assertEquals(message1.hashCode(), message2.hashCode());
        assertNotEquals(message2.hashCode(), message3.hashCode());
    }
}