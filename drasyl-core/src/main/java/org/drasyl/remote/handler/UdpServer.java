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
package org.drasyl.remote.handler;

import com.google.common.hash.Hashing;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.Node;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.Identity;
import org.drasyl.peer.Endpoint;
import org.drasyl.pipeline.DrasylPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.remote.protocol.AddressedByteBuf;
import org.drasyl.util.FutureUtil;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.UnsignedInteger;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.NettyUtil.getBestDatagramChannel;
import static org.drasyl.util.NetworkUtil.MAX_PORT_NUMBER;
import static org.drasyl.util.NetworkUtil.getAddresses;

/**
 * Binds to a udp port, sends outgoing messages via udp, and sends received udp packets to the
 * {@link DrasylPipeline}.
 */
public class UdpServer extends SimpleOutboundHandler<AddressedByteBuf, InetSocketAddressWrapper> {
    public static final String UDP_SERVER = "UDP_SERVER";
    private static final Logger LOG = LoggerFactory.getLogger(UdpServer.class);
    private static final short MIN_DERIVED_PORT = 22528;
    private final Bootstrap bootstrap;
    private Channel channel;

    UdpServer(final Bootstrap bootstrap,
              final Channel channel) {
        this.bootstrap = requireNonNull(bootstrap);
        this.channel = channel;
    }

    public UdpServer(final EventLoopGroup bossGroup) {
        this(
                new Bootstrap()
                        .group(bossGroup)
                        .channel(getBestDatagramChannel())
                        .option(ChannelOption.SO_BROADCAST, false),
                null
        );
    }

    static Set<Endpoint> determineActualEndpoints(final Identity identity,
                                                  final DrasylConfig config,
                                                  final InetSocketAddress listenAddress) {
        final Set<Endpoint> configEndpoints = config.getRemoteEndpoints();
        if (!configEndpoints.isEmpty()) {
            // read endpoints from config
            return configEndpoints.stream().map(endpoint -> {
                if (endpoint.getPort() == 0) {
                    return Endpoint.of(endpoint.getHost(), listenAddress.getPort(), identity.getPublicKey());
                }
                return endpoint;
            }).collect(Collectors.toSet());
        }

        final Set<InetAddress> addresses;
        if (listenAddress.getAddress().isAnyLocalAddress()) {
            // use all available addresses
            addresses = getAddresses();
        }
        else {
            // use given host
            addresses = Set.of(listenAddress.getAddress());
        }
        return addresses.stream()
                .map(address -> Endpoint.of(address.getHostAddress(), listenAddress.getPort(), identity.getPublicKey()))
                .collect(Collectors.toSet());
    }

    @Override
    public void eventTriggered(final HandlerContext ctx,
                               final Event event,
                               final CompletableFuture<Void> future) {
        if (event instanceof NodeUpEvent) {
            startServer(ctx, event, future);
        }
        else if (event instanceof NodeUnrecoverableErrorEvent || event instanceof NodeDownEvent) {
            stopServer(ctx, event, future);
        }
        else {
            // passthrough event
            ctx.fireEventTriggered(event, future);
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    private synchronized void startServer(final HandlerContext ctx,
                                          final Event event,
                                          final CompletableFuture<Void> future) {
        if (channel == null) {
            LOG.debug("Start Server...");
            final int bindPort;
            if (ctx.config().getRemoteBindPort() == -1) {
                /*
                 derive a port in the range between MIN_DERIVED_PORT and {MAX_PORT_NUMBER from its
                 own identity. this is done because we also expose this port via
                 UPnP-IGD/NAT-PMP/PCP and some NAT devices behave unexpectedly when multiple nodes
                 in the local network try to expose the same local port.
                 a completely random port would have the disadvantage that every time the node is
                 started it would use a new port and this would make discovery more difficult
                */
                final long identityHash = UnsignedInteger.of(Hashing.murmur3_32().hashBytes(ctx.identity().getPublicKey().byteArrayValue()).asBytes()).getValue();
                bindPort = (int) (MIN_DERIVED_PORT + identityHash % (MAX_PORT_NUMBER - MIN_DERIVED_PORT));
            }
            else {
                bindPort = ctx.config().getRemoteBindPort();
            }
            final ChannelFuture channelFuture = bootstrap
                    .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                        @Override
                        protected void channelRead0(final ChannelHandlerContext channelCtx,
                                                    final DatagramPacket packet) {
                            LOG.trace("Datagram received {}", packet);
                            final AddressedByteBuf addressedByteBuf = new AddressedByteBuf(packet.sender(), packet.recipient(), packet.content().retain());
                            ctx.fireRead(addressedByteBuf.getSender(), addressedByteBuf, new CompletableFuture<>());
                        }
                    })
                    .bind(ctx.config().getRemoteBindHost(), bindPort);
            channelFuture.awaitUninterruptibly();

            if (channelFuture.isSuccess()) {
                // server successfully started
                this.channel = channelFuture.channel();
                final InetSocketAddress socketAddress = (InetSocketAddress) channel.localAddress();
                LOG.info("Server started and listening at {}", socketAddress);

                // consume NodeUpEvent and publish NodeUpEvent with port
                ctx.fireEventTriggered(NodeUpEvent.of(Node.of(ctx.identity(), socketAddress.getPort())), future);
            }
            else {
                // server start failed
                LOG.warn("Unable to bind server to address {}:{}: {}", ctx.config()::getRemoteBindHost, () -> bindPort, channelFuture.cause()::getMessage);

                future.completeExceptionally(new Exception("Unable to bind server to address " + ctx.config().getRemoteBindHost() + ":" + bindPort + ": " + channelFuture.cause().getMessage()));
            }
        }
        else {
            // passthrough event
            ctx.fireEventTriggered(event, future);
        }
    }

    private synchronized void stopServer(final HandlerContext ctx,
                                         final Event event,
                                         final CompletableFuture<Void> future) {
        if (channel != null) {
            final InetSocketAddress socketAddress = (InetSocketAddress) channel.localAddress();
            LOG.debug("Stop Server listening at {}...", socketAddress);
            // shutdown server
            channel.close().awaitUninterruptibly();
            channel = null;
            LOG.debug("Server stopped");
        }

        // passthrough event
        ctx.fireEventTriggered(event, future);
    }

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final InetSocketAddressWrapper recipient,
                                final AddressedByteBuf addressedByteBuf,
                                final CompletableFuture<Void> future) {
        if (channel != null && channel.isWritable()) {
            final DatagramPacket packet = new DatagramPacket(addressedByteBuf.getContent(), addressedByteBuf.getRecipient());
            LOG.trace("Send Datagram {}", packet);
            FutureUtil.completeOnAllOf(future, FutureUtil.toFuture(channel.writeAndFlush(packet)));
        }
        else {
            ReferenceCountUtil.safeRelease(addressedByteBuf);
            future.completeExceptionally(new Exception("Udp Channel is not present or is not writable."));
        }
    }
}
