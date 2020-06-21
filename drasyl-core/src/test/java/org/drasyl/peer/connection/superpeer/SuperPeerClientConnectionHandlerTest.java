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
package org.drasyl.peer.connection.superpeer;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static java.time.Duration.ofMillis;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_SERVICE_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperPeerClientConnectionHandlerTest {
    private EmbeddedChannel channel;
    @Mock
    private Messenger messenger;
    @Mock
    private QuitMessage quitMessage;
    @Mock
    private CompletableFuture<Void> handshakeFuture;
    @Mock
    private ScheduledFuture<?> timeoutFuture;
    @Mock
    private JoinMessage requestMessage;
    @Mock
    private StatusMessage statusMessage;
    @Mock
    private DrasylNodeConfig config;
    @Mock
    private SuperPeerClient client;

    @Test
    void shouldCloseChannelOnQuitMessage() {
        when(handshakeFuture.isDone()).thenReturn(true);

        SuperPeerClientConnectionHandler handler = new SuperPeerClientConnectionHandler(config, client, ofMillis(1000), messenger, handshakeFuture, timeoutFuture, requestMessage);
        channel = new EmbeddedChannel(handler);
        channel.readOutbound(); // join message
        channel.flush();

        channel.writeInbound(quitMessage);
        channel.flush();

        assertFalse(channel.isOpen());
    }

    @Test
    void shouldFailHandshakeIfServerReplyWithStatusUnavailableOnWelcomeMessage() {
        when(handshakeFuture.isDone()).thenReturn(false);
        when(requestMessage.getId()).thenReturn("123");
        when(statusMessage.getCorrespondingId()).thenReturn("123");
        when(statusMessage.getCode()).thenReturn(STATUS_SERVICE_UNAVAILABLE);

        SuperPeerClientConnectionHandler handler = new SuperPeerClientConnectionHandler(config, client, ofMillis(1000), messenger, handshakeFuture, timeoutFuture, requestMessage);
        channel = new EmbeddedChannel(handler);
        channel.readOutbound(); // join message
        channel.flush();

        channel.writeInbound(statusMessage);

        verify(handshakeFuture).completeExceptionally(any());
    }
}