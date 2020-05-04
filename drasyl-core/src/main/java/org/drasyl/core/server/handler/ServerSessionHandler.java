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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.core.common.messages.IMessage;
import org.drasyl.core.common.messages.Join;
import org.drasyl.core.common.messages.NodeServerException;
import org.drasyl.core.common.messages.Response;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.server.NodeServer;
import org.drasyl.core.server.actions.ServerAction;
import org.drasyl.core.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * This handler mange in-/oncoming messages and pass them to the correct sub-function. It also
 * creates a new {@link ServerSession} object if a {@link Join} has pass the {@link JoinHandler}
 * guard.
 */
public class ServerSessionHandler extends SimpleChannelInboundHandler<ServerAction> {
    private static final Logger LOG = LoggerFactory.getLogger(ServerSessionHandler.class);
    private final NodeServer server;
    private final CompletableFuture<ServerSession> sessionReadyFuture;
    private ServerSession serverSession;
    private URI uri;

    /**
     * Creates a new instance of this {@link io.netty.channel.ChannelHandler}
     *
     * @param server a reference to this server instance
     */
    public ServerSessionHandler(NodeServer server) {
        this(server, null, new CompletableFuture<>());
    }

    /**
     * Creates a new instance of this {@link io.netty.channel.ChannelHandler} and completes the
     * given future, when the {@link ServerSession} was created.
     *
     * @param server               a reference to this node server instance
     * @param uri                  the {@link URI} of the newly created {@link ServerSession}, null
     *                             to let this class guess the correct IP
     * @param sessionReadyListener the future, that should be completed a serverSession creation
     */
    public ServerSessionHandler(NodeServer server,
                                URI uri,
                                CompletableFuture<ServerSession> sessionReadyListener) {
        this.sessionReadyFuture = sessionReadyListener;
        this.server = server;
        this.uri = uri;
    }

    /*
     * Adds a listener to the channel close event, to remove the serverSession from various lists.
     */
    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        ctx.channel().closeFuture().addListener(future -> {
            if (serverSession != null) {
                server.getPeersManager().getPeer(serverSession.getIdentity()).removePeerConnection(serverSession);
            }
        });
    }

    /*
     * Reads an incoming message and pass it to the correct sub-function.
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ServerAction msg) throws Exception {
        createSession(ctx, msg);

        ctx.executor().submit(() -> {
            if (serverSession != null) {
                msg.onMessage(serverSession, server);
            }
        }).addListener(future -> {
            if (!future.isSuccess()) {
                LOG.debug("Could not process the message {}: ", msg, future.cause());
            }
            ReferenceCountUtil.release(msg);
        });
    }

    /**
     * Creates a serverSession, if not already there.
     *
     * @param ctx channel handler context
     * @param msg probably a {@link Join}
     */
    private void createSession(final ChannelHandlerContext ctx, IMessage msg) {
        if (msg instanceof Join && serverSession == null) {
            Join jm = (Join) msg;
            Identity identity = Identity.of(jm.getPublicKey());
            Channel myChannel = ctx.channel();

            boolean alreadyOpenConnection = server.getPeersManager().getChildren().contains(identity);
            if (!alreadyOpenConnection && server.getPeersManager().getPeer(identity) != null) {
                alreadyOpenConnection = server.getPeersManager().getPeer(identity).getConnections().stream().anyMatch(peerConnection -> peerConnection.getConnectionId().equals(myChannel.id().asLongText()));
            }

            if (!alreadyOpenConnection) {
                if (uri == null) {
                    try {
                        uri = getRemoteAddr(myChannel);
                    }
                    catch (URISyntaxException e) {
                        LOG.error("Cannot determine URI: ", e);
                    }
                }

                serverSession = new ServerSession(ctx.channel(), uri, identity,
                        Optional.ofNullable(jm.getUserAgent()).orElse("U/A"));
                sessionReadyFuture.complete(serverSession);
                LOG.debug("Create new channel {}, for ServerSession {}", ctx.channel().id(), serverSession);
            }
            else {
                ctx.writeAndFlush(new Response<>(
                        new NodeServerException(
                                "This client has already an open session with this node server. Can't open more sockets."),
                        jm.getMessageID()));
                ctx.close();
            }
        }
    }

    /**
     * Returns the {@link URI} of a {@link Channel}.
     *
     * @param channel the channel
     */
    public static URI getRemoteAddr(Channel channel) throws URISyntaxException {
        InetSocketAddress socketAddress = (InetSocketAddress) channel.remoteAddress();
        return new URI("ws://" + socketAddress.getAddress().getHostAddress() + ":" + socketAddress.getPort() + "/"); // NOSONAR
    }
}