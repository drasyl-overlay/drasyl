/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package city.sane.relay.server.actions.messages;

import city.sane.relay.server.RelayServer;
import city.sane.relay.server.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ServerActionPongTest {
    private Session session;
    private RelayServer relay;
    private String responseMsgID;

    @BeforeEach
    void setUp() {
        session = mock(Session.class);
        relay = mock(RelayServer.class);

        responseMsgID = "id";
    }

    @Test
    void onMessage() {
        ServerActionPong message = new ServerActionPong();

        message.onMessage(session, relay);

        verifyNoInteractions(session);
        verifyNoInteractions(relay);
    }

    @Test
    void onResponse() {
        ServerActionPong message = new ServerActionPong();

        message.onResponse(responseMsgID, session, relay);

        verifyNoInteractions(session);
        verifyNoInteractions(relay);
    }
}