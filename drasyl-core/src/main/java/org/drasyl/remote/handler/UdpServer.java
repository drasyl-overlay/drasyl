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
package org.drasyl.remote.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.flush.FlushConsolidationHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
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
import org.drasyl.util.DrasylScheduler;
import org.drasyl.util.FutureUtil;
import org.drasyl.util.Pair;
import org.drasyl.util.PortMappingUtil;
import org.drasyl.util.PortMappingUtil.PortMapping;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.SetUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.netty.handler.flush.FlushConsolidationHandler.DEFAULT_EXPLICIT_FLUSH_AFTER_FLUSHES;
import static java.util.Optional.ofNullable;
import static org.drasyl.util.NetworkUtil.getAddresses;
import static org.drasyl.util.ObservableUtil.pairWithPreviousObservable;
import static org.drasyl.util.PortMappingUtil.Protocol.UDP;
import static org.drasyl.util.UriUtil.createUri;
import static org.drasyl.util.UriUtil.overridePort;

/**
 * Binds to a udp port, sends outgoing messages via udp, and sends received udp packets to the
 * {@link DrasylPipeline}.
 */
public class UdpServer extends SimpleOutboundHandler<ByteBuf, InetSocketAddressWrapper> {
    public static final String UDP_SERVER = "UDP_SERVER";
    private static final Logger LOG = LoggerFactory.getLogger(UdpServer.class);
    private final Bootstrap bootstrap;
    private final Scheduler scheduler;
    private final Function<InetSocketAddress, Set<PortMapping>> portExposer;
    private Set<Endpoint> actualEndpoints;
    private Channel channel;
    private Set<PortMapping> portMappings;
    private final ConcurrentLinkedQueue<Pair<DatagramPacket, ChannelPromise>> pendingPackages;
    private final AtomicBoolean queueRunning;

    UdpServer(final Bootstrap bootstrap,
              final Scheduler scheduler,
              final Function<InetSocketAddress, Set<PortMapping>> portExposer,
              final Channel channel) {
        this.bootstrap = bootstrap;
        this.scheduler = scheduler;
        this.portExposer = portExposer;
        this.channel = channel;
        this.pendingPackages = new ConcurrentLinkedQueue<>();
        this.queueRunning = new AtomicBoolean(false);
    }

    public UdpServer(final EventLoopGroup bossGroup) {
        this(
                new Bootstrap()
                        .group(bossGroup)
                        .channel(getBestDatagramChannel())
                        .option(ChannelOption.SO_BROADCAST, true),
                DrasylScheduler.getInstanceHeavy(),
                address -> PortMappingUtil.expose(address, UDP),
                null
        );
        actualEndpoints = Set.of();
    }

    /**
     * Returns the {@link DatagramChannel} that fits best to the current environment. Under Linux
     * the more performant {@link EpollDatagramChannel} is returned.
     *
     * @return {@link DatagramChannel} that fits best to the current environment
     */
    static Class<? extends DatagramChannel> getBestDatagramChannel() {
        if (Epoll.isAvailable()) {
            return EpollDatagramChannel.class;
        }
        else {
            return NioDatagramChannel.class;
        }
    }

    static Set<Endpoint> determineActualEndpoints(final Identity identity,
                                                  final DrasylConfig config,
                                                  final InetSocketAddress listenAddress) {
        final Set<Endpoint> configEndpoints = config.getRemoteEndpoints();
        if (!configEndpoints.isEmpty()) {
            // read endpoints from config
            return configEndpoints.stream().map(endpoint -> {
                if (endpoint.getPort() == 0) {
                    return Endpoint.of(overridePort(endpoint.getURI(), listenAddress.getPort()), identity.getPublicKey());
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
                .map(address -> createUri("udp", address.getHostAddress(), listenAddress.getPort()))
                .map(address -> Endpoint.of(address, identity.getPublicKey()))
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

    private synchronized void startServer(final HandlerContext ctx,
                                          final Event event,
                                          final CompletableFuture<Void> future) {
        if (channel == null) {
            LOG.debug("Start Server...");
            bootstrap.option(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(ctx.config().getRemoteMessageMtu()));
            bootstrap.option(ChannelOption.SO_RCVBUF, ctx.config().getRemoteMessageMaxContentLength() * 10);
            bootstrap.option(ChannelOption.SO_SNDBUF, ctx.config().getRemoteMessageMaxContentLength() * 10);
            bootstrap.option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(ctx.config().getRemoteMessageMtu(), ctx.config().getRemoteMessageMtu() * 10));

            final ChannelFuture channelFuture = bootstrap
                    .handler(new ChannelInitializer<DatagramChannel>() {
                        @Override
                        protected void initChannel(final DatagramChannel ch) {
                            final ChannelPipeline pipeline = ch.pipeline();

                            // Use buffer for better IO performance
                            pipeline.addLast(new ChannelTrafficShapingHandler(1024 * 500, 0));
                            pipeline.addLast(
                                    new FlushConsolidationHandler(DEFAULT_EXPLICIT_FLUSH_AFTER_FLUSHES, true));
                            pipeline.addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                                @Override
                                public void channelWritabilityChanged(final ChannelHandlerContext channelCtx) {
                                    LOG.error("Writability changed to {}", channelCtx.channel().isWritable());
                                    pullQueue(ctx);

                                    channelCtx.fireChannelWritabilityChanged();
                                }

                                @Override
                                protected void channelRead0(final ChannelHandlerContext channelCtx,
                                                            final DatagramPacket msg) {
                                    LOG.trace("Datagram received {}", msg);
                                    final ByteBuf byteBuf = msg.content();
                                    byteBuf.retain();
                                    ctx.pipeline().processInbound(InetSocketAddressWrapper.of(msg.sender()), byteBuf);
                                }
                            });
                        }
                    })
                    .bind(ctx.config().getRemoteBindHost(), ctx.config().getRemoteBindPort());
            channelFuture.awaitUninterruptibly();

            if (channelFuture.isSuccess()) {
                // server successfully started
                this.channel = channelFuture.channel();
                final InetSocketAddress socketAddress = (InetSocketAddress) channel.localAddress();
                actualEndpoints = determineActualEndpoints(ctx.identity(), ctx.config(), socketAddress);
                LOG.debug("Server started and listening at {}", socketAddress);

                if (ctx.config().isRemoteExposeEnabled()) {
                    exposeEndpoints(ctx, socketAddress);
                }

                // consume NodeUpEvent and publish NodeUpEvent with endpoint
                ctx.fireEventTriggered(new NodeUpEvent(Node.of(ctx.identity(), actualEndpoints)), future);
            }
            else {
                // server start failed
                LOG.warn("Unable to bind server to address {}:{}: {}", ctx.config().getRemoteBindHost(), ctx.config().getRemoteBindPort(), channelFuture.cause().getMessage());

                future.completeExceptionally(new Exception("Unable to bind server to address " + ctx.config().getRemoteBindHost() + ":" + ctx.config().getRemoteBindPort() + ": " + channelFuture.cause().getMessage()));
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
            unexposeEndpoints();
            channel.close().awaitUninterruptibly();
            channel = null;
            LOG.debug("Server stopped");
        }

        // passthrough event
        ctx.fireEventTriggered(event, future);
    }

    private void pullQueue(final HandlerContext ctx) {
        if (channel.isWritable() && queueRunning.compareAndSet(false, true)) {
            ctx.scheduler().scheduleDirect(() -> {
                while (channel.isWritable()) {
                    final Pair<DatagramPacket, ChannelPromise> packet = pendingPackages.poll();

                    if (packet != null) {
                        LOG.trace("Send Datagram {}", packet.first());
                        channel.writeAndFlush(packet.first(), packet.second()).awaitUninterruptibly();
                    }
                    else {
                        // Nothing to send
                        break;
                    }
                }
                queueRunning.set(false);
            });
        }
    }

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final InetSocketAddressWrapper recipient,
                                final ByteBuf byteBuf,
                                final CompletableFuture<Void> future) {
        if (channel != null) {
            final DatagramPacket packet = new DatagramPacket(byteBuf, recipient.getAddress());
            final ChannelPromise promise = channel.newPromise();

            pendingPackages.add(Pair.of(packet, promise));
            FutureUtil.completeOnAllOf(future, FutureUtil.toFuture(promise));

            pullQueue(ctx);
        }
        else {
            ReferenceCountUtil.safeRelease(byteBuf);
            future.completeExceptionally(new Exception("Udp Channel is not present or is not writable."));
        }
    }

    void exposeEndpoints(final HandlerContext ctx,
                         final InetSocketAddress socketAddress) {
        scheduler.scheduleDirect(() -> {
            // create port mappings
            portMappings = portExposer.apply(socketAddress);

            // we need to observe these mappings because they can change or fail over time
            final List<Observable<Optional<InetSocketAddress>>> externalAddressObservables = portMappings.stream().map(PortMapping::externalAddress).collect(Collectors.toList());
            @SuppressWarnings("unchecked") final Observable<Set<InetSocketAddress>> allExternalAddressesObservable = Observable.combineLatest(
                    externalAddressObservables,
                    newMappings -> Arrays.stream(newMappings).map(o -> (Optional<InetSocketAddress>) o).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toSet())
            );
            final Observable<Pair<Set<InetSocketAddress>, Set<InetSocketAddress>>> allExternalAddressesChangesObservable = pairWithPreviousObservable(allExternalAddressesObservable);

            allExternalAddressesChangesObservable.subscribe(pair -> {
                final Set<InetSocketAddress> currentAddresses = pair.first();
                final Set<InetSocketAddress> previousAddresses = ofNullable(pair.second()).orElse(Set.of());

                final Set<InetSocketAddress> addressesToRemove = SetUtil.difference(previousAddresses, currentAddresses);
                final Set<Endpoint> endpointsToRemove = addressesToRemove.stream()
                        .map(address -> createUri("udp", address.getHostName(), address.getPort()))
                        .map(address -> Endpoint.of(address, ctx.identity().getPublicKey()))
                        .collect(Collectors.toSet());
                actualEndpoints.removeAll(endpointsToRemove);

                final Set<InetSocketAddress> addressesToAdd = SetUtil.difference(currentAddresses, previousAddresses);
                final Set<Endpoint> endpointsToAdd = addressesToAdd.stream()
                        .map(address -> createUri("udp", address.getHostName(), address.getPort()))
                        .map(address -> Endpoint.of(address, ctx.identity().getPublicKey()))
                        .collect(Collectors.toSet());
                actualEndpoints.addAll(endpointsToAdd);
            });
        });
    }

    private void unexposeEndpoints() {
        scheduler.scheduleDirect(() -> {
            if (portMappings != null) {
                portMappings.forEach(PortMapping::close);
                portMappings = null;
            }
        });
    }
}
