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

package org.drasyl.peer.connection.message.action;

import com.typesafe.config.ConfigFactory;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.messenger.MessengerException;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.drasyl.peer.connection.server.NodeServer;
import org.drasyl.peer.connection.server.NodeServerClientConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_NOT_FOUND;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ApplicationMessageActionTest {
    private ApplicationMessage message;
    private NodeServerClientConnection clientConnection;
    private NodeServer nodeServer;
    private Identity sender, recipient;
    private byte[] payload;
    private String id;
    private PeersManager peersManager;
    private Messenger messenger;

    @BeforeEach
    void setUp() {
        message = mock(ApplicationMessage.class);
        clientConnection = mock(NodeServerClientConnection.class);
        nodeServer = mock(NodeServer.class);
        peersManager = mock(PeersManager.class);
        sender = mock(Identity.class);
        recipient = mock(Identity.class);
        messenger = mock(Messenger.class);
        payload = new byte[]{ 0x00, 0x01, 0x03 };
        id = "id";

        when(nodeServer.getPeersManager()).thenReturn(peersManager);
        when(nodeServer.getConfig()).thenReturn(new DrasylNodeConfig(ConfigFactory.load()));
        when(nodeServer.getMessenger()).thenReturn(messenger);

        when(message.getSender()).thenReturn(sender);
        when(message.getRecipient()).thenReturn(recipient);
        when(message.getPayload()).thenReturn(payload);
        when(message.getId()).thenReturn(id);
    }

    @Test
    void onMessageServerShouldSendStatusOkIfMessageCouldBeSent() throws DrasylException {
        ApplicationMessageAction action = new ApplicationMessageAction(message);
        action.onMessageServer(clientConnection, nodeServer);

        verify(clientConnection).send(new StatusMessage(STATUS_OK, message.getId()));
        verify(messenger).send(message);
    }

    @Test
    void onMessageServerShouldSendStatusNotFoundIfMessageCouldNotBeSent() throws DrasylException {
        doThrow(MessengerException.class).when(messenger).send(any());

        ApplicationMessageAction action = new ApplicationMessageAction(message);
        action.onMessageServer(clientConnection, nodeServer);

        verify(clientConnection).send(new StatusMessage(STATUS_NOT_FOUND, message.getId()));
    }

    @Test
    void onMessageServerShouldRejectNullValues() throws DrasylException {
        ApplicationMessageAction action = new ApplicationMessageAction(message);

        assertThrows(NullPointerException.class, () -> action.onMessageServer(null, nodeServer));
        assertThrows(NullPointerException.class, () -> action.onMessageServer(clientConnection, null));

        verify(clientConnection, never()).send(any());
        verify(messenger, never()).send(any());
    }
}