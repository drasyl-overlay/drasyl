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
package org.drasyl.core.server.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.core.common.messages.*;
import org.drasyl.core.models.CompressedPublicKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class JoinHandlerTest {
    private ChannelHandlerContext ctx;
    private ScheduledFuture future;
    private ChannelPromise promise;
    private IMessage msg;
    private CompressedPublicKey publicKey;

    @BeforeEach
    void setUp() {
        ctx = mock(ChannelHandlerContext.class);
        promise = mock(ChannelPromise.class);
        future = mock(ScheduledFuture.class);
        msg = new Leave();
        publicKey = mock(CompressedPublicKey.class);
    }

    @Test
    void close() throws Exception {
        JoinHandler handler = new JoinHandler(false, 1L, future);

        handler.close(ctx, promise);

        verify(future, times(1)).cancel(true);
        verify(ctx, times(1)).close(promise);
    }

    @Test
    void channelWrite0() throws Exception {
        JoinHandler handler = new JoinHandler(true, 1L, future);

        handler.channelWrite0(ctx, msg);

        verify(ctx, times(1)).write(any(Leave.class));
    }

    @Test
    void channelWrite0UnrestrictedPassableMessage() throws Exception {
        JoinHandler handler = new JoinHandler(1L);

        handler.channelWrite0(ctx, msg);

        verify(ctx, times(1)).write(any(Leave.class));
    }

    @Test
    void channelWrite0NoAuth() throws Exception {
        JoinHandler handler = new JoinHandler(false, 1L, future);
        msg = new RequestClientsStocktaking();

        assertThrows(IllegalStateException.class, () -> {
            handler.channelWrite0(ctx, msg);
        });

        verify(ctx, never()).write(any(IMessage.class));
    }

    @Test
    void channelRead0() throws Exception {
        JoinHandler handler = new JoinHandler(false, 1L, future);

        handler.channelRead0(ctx, msg);

        verify(ctx, times(1)).writeAndFlush(any(Response.class));
        verify(ctx, never()).fireChannelRead(any(IMessage.class));
    }

    @Test
    void channelRead0Join() throws Exception {
        JoinHandler handler = new JoinHandler(false, 1L, future);
        msg = mock(Join.class);

        handler.channelRead0(ctx, msg);

        verify(ctx, never()).writeAndFlush(any(Response.class));
        verify(future, times(1)).cancel(true);
        verify(ctx, times(1)).fireChannelRead(any(Join.class));
        assertTrue(handler.authenticated);
    }

    @Test
    void channelRead0DoubleJoin() throws Exception {
        JoinHandler handler = new JoinHandler(true, 1L, future);
        msg = new Join(publicKey, Set.of());

        handler.channelRead0(ctx, msg);

        verify(ctx, times(1)).writeAndFlush(any(Response.class));
        verify(future, never()).cancel(true);
        verify(ctx, never()).fireChannelRead(any(Join.class));
        assertTrue(handler.authenticated);
    }

    @Test
    void channelRead0Auth() throws Exception {
        JoinHandler handler = new JoinHandler(true, 1L, future);

        handler.channelRead0(ctx, msg);

        verify(ctx, never()).writeAndFlush(any(Response.class));
        verify(future, never()).cancel(true);
        verify(ctx, never()).fireChannelRead(any(Join.class));
        assertTrue(handler.authenticated);
    }
}