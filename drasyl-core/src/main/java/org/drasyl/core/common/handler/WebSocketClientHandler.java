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

package org.drasyl.core.common.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import org.drasyl.core.common.messages.IMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class WebSocketClientHandler extends SimpleChannelDuplexHandler<Object, IMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(WebSocketClientHandler.class);
    private final WebSocketClientHandshaker handshaker;
    private final CompletableFuture<Void> handshakeFuture;

    public WebSocketClientHandler(WebSocketClientHandshaker handshaker) {
        this.handshaker = handshaker;
        this.handshakeFuture = new CompletableFuture<>();
    }

    public CompletableFuture<Void> handshakeFuture() {
        return handshakeFuture;
    }


    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        handshaker.handshake(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (LOG.isDebugEnabled())
            LOG.debug("[{}] WebSocket Client disconnected!", ctx.channel().id().asShortText());
    }

    @Override
    protected void channelWrite0(ChannelHandlerContext ctx, IMessage msg) throws Exception {
        handshakeFuture.get();
        ctx.write(msg);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            try {
                handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                handshakeFuture.complete(null);

                if (LOG.isDebugEnabled())
                    LOG.debug("[{}] WebSocket Client connected!", ch.id().asShortText());
            } catch (WebSocketHandshakeException e) {
                handshakeFuture.completeExceptionally(e);
                LOG.error("[{}] WebSocket Client failed to connect!", ch.id().asShortText());
            }
            return;
        }

        if (msg instanceof FullHttpResponse) {
            FullHttpResponse response = (FullHttpResponse) msg;
            throw new IllegalStateException(
                    "Unexpected FullHttpResponse (getStatus=" + response.status() +
                            ", content=" + response.content().toString(StandardCharsets.UTF_8) + ')');
        }

        WebSocketFrame frame = (WebSocketFrame) msg;
        if (frame instanceof CloseWebSocketFrame) {
            ch.close();

            if (LOG.isDebugEnabled())
                LOG.debug("[{}] WebSocket was closed!", ch.id().asShortText());
        } else {
            ctx.fireChannelRead(((WebSocketFrame) msg).retain());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.error("", cause);
        if (!handshakeFuture.isDone()) {
            handshakeFuture.completeExceptionally(cause);
        }
        ctx.close();
    }
}