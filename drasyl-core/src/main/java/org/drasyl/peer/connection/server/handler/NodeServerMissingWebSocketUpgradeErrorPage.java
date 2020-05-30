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

package org.drasyl.peer.connection.server.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.drasyl.DrasylNode;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityManager;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.SERVER;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.HEAD;
import static io.netty.handler.codec.http.HttpResponseStatus.*;

/**
 * This handler returns an HTML error page if the HTTP request does not perform a Websocket
 * upgrade.
 */
public class NodeServerMissingWebSocketUpgradeErrorPage extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final IdentityManager identityManager;

    public NodeServerMissingWebSocketUpgradeErrorPage(IdentityManager identityManager) {
        this.identityManager = identityManager;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        // pass through websocket request
        if (req.headers().containsValue(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE, true)
                || HttpHeaderValues.WEBSOCKET.contentEqualsIgnoreCase(req.headers().get(HttpHeaderNames.UPGRADE))) {
            ctx.fireChannelRead(req.retain());
            return;
        }

        // response with node information on HEAD request
        if (HEAD.equals(req.method())) {
            Identity identity = identityManager.getIdentity();
            generateHeaders(ctx, req, identity, OK);
            return;
        }

        // allow only GET request
        if (!GET.equals(req.method())) {
            sendHttpResponse(ctx, new DefaultFullHttpResponse(req.protocolVersion(), FORBIDDEN,
                    ctx.alloc().buffer(0)));
            return;
        }

        if ("/".equals(req.uri()) || "/index.html".equals(req.uri()) || "/index.htm".equals(req.uri())) {
            // display custom bad request error page for root path
            Identity identity = identityManager.getIdentity();
            generateHeaders(ctx, req, identity, BAD_REQUEST);
        }
        else {
            // return "not found" for all other pathes
            sendHttpResponse(ctx, new DefaultFullHttpResponse(req.protocolVersion(), NOT_FOUND,
                    ctx.alloc().buffer(0)));
        }
    }

    private static void generateHeaders(ChannelHandlerContext ctx,
                                        FullHttpRequest req,
                                        Identity identity,
                                        HttpResponseStatus status) {
        ByteBuf content = getContent(identity);
        FullHttpResponse res = new DefaultFullHttpResponse(req.protocolVersion(), status, content);
        res.headers().set("x-identity", identity.getId());
        res.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");
        HttpUtil.setContentLength(res, content.readableBytes());
        sendHttpResponse(ctx, res);
    }

    private static void sendHttpResponse(ChannelHandlerContext ctx,
                                         FullHttpResponse res) {
        res.headers().set(SERVER, "drasyl/" + DrasylNode.getVersion());

        // add http code reason phrase if content is empty
        if (res.content().readableBytes() == 0) {
            HttpResponseStatus responseStatus = res.status();
            ByteBufUtil.writeUtf8(res.content(), responseStatus.toString());
            HttpUtil.setContentLength(res, res.content().readableBytes());
        }
        // Send the response and close the connection
        ChannelFuture future = ctx.writeAndFlush(res);
        future.addListener(ChannelFutureListener.CLOSE);
    }

    public static ByteBuf getContent(Identity identity) {
        return Unpooled.copiedBuffer(
                "\n" +
                        "<!DOCTYPE HTML PUBLIC \"-//IETF//DTD HTML 2.0//EN\">\n" +
                        "<html><head>\n" +
                        "<title>400 Bad Request</title>\n" +
                        "</head><body>\n" +
                        "<h1>Bad Request</h1>\n" +
                        "<p>Not a WebSocket Handshake Request: Missing Upgrade.</p>\n" +
                        "<hr>\n" +
                        "<address>drasyl/" + DrasylNode.getVersion() + " with Identity " + identity.getId() + "</address>\n" +
                        "</body></html>\n", CharsetUtil.UTF_8);
    }
}