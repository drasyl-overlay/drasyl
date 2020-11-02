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
package org.drasyl;

import com.google.common.annotations.Beta;
import com.typesafe.config.ConfigException;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.crypto.CryptoException;
import org.drasyl.event.Event;
import org.drasyl.event.Node;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityManager;
import org.drasyl.monitoring.Monitoring;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.client.SuperPeerClient;
import org.drasyl.peer.connection.direct.DirectConnectionsManager;
import org.drasyl.peer.connection.intravm.IntraVmDiscovery;
import org.drasyl.peer.connection.localhost.LocalHostDiscovery;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.pipeline.DirectConnectionMessageSinkHandler;
import org.drasyl.peer.connection.pipeline.LoopbackMessageSinkHandler;
import org.drasyl.peer.connection.pipeline.SuperPeerMessageSinkHandler;
import org.drasyl.peer.connection.server.Server;
import org.drasyl.pipeline.DrasylPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.OtherNetworkFilter;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.codec.Codec;
import org.drasyl.plugins.PluginManager;
import org.drasyl.util.FutureUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.drasyl.peer.connection.handler.ThreeWayHandshakeClientHandler.ATTRIBUTE_PUBLIC_KEY;
import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_SHUTTING_DOWN;
import static org.drasyl.peer.connection.pipeline.DirectConnectionMessageSinkHandler.DIRECT_CONNECTION_MESSAGE_SINK_HANDLER;
import static org.drasyl.peer.connection.pipeline.LoopbackMessageSinkHandler.LOOPBACK_MESSAGE_SINK_HANDLER;
import static org.drasyl.peer.connection.pipeline.SuperPeerMessageSinkHandler.SUPER_PEER_SINK_HANDLER;
import static org.drasyl.pipeline.OtherNetworkFilter.OTHER_NETWORK_FILTER;
import static org.drasyl.util.DrasylScheduler.getInstanceHeavy;

/**
 * Represents a node in the drasyl Overlay Network. Applications that want to run on drasyl must
 * implement this class.
 * <p>
 * Example usage:
 * <pre><code>
 * DrasylNode node = new DrasylNode() {
 *   &#64;Override
 *   public void onEvent(Event event) {
 *     // handle incoming events (messages) here
 *     System.out.println("Event received: " + event);
 *   }
 * };
 * node.start();
 *
 * // wait till NodeOnlineEvent has been received
 *
 * // send message to another node
 * node.send("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9", "Hello World");
 *
 * // shutdown node
 * node.shutdown();
 * </code></pre>
 */
@SuppressWarnings({ "java:S107" })
@Beta
public abstract class DrasylNode {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylNode.class);
    private static final List<DrasylNode> INSTANCES;
    private static volatile boolean workerGroupCreated = false;
    private static volatile boolean bossGroupCreated = false;

    static {
        // https://github.com/netty/netty/issues/7817
        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
        INSTANCES = Collections.synchronizedList(new ArrayList<>());
    }

    private final DrasylConfig config;
    private final Identity identity;
    private final PeersManager peersManager;
    private final PeerChannelGroup channelGroup;
    private final Set<Endpoint> endpoints;
    private final AtomicBoolean acceptNewConnections;
    private final Pipeline pipeline;
    private final List<DrasylNodeComponent> components;
    private final AtomicBoolean started;
    private final PluginManager pluginManager;
    private CompletableFuture<Void> startSequence;
    private CompletableFuture<Void> shutdownSequence;

    /**
     * Creates a new drasyl Node. The node is only being created, it neither connects to the Overlay
     * Network, nor can send or receive messages. To do this you have to call {@link #start()}.
     * <p>
     * Note: This is a blocking method, because when a node is started for the first time, its
     * identity must be created. This can take up to a minute because of the proof of work.
     */
    protected DrasylNode() throws DrasylException {
        this(new DrasylConfig());
    }

    /**
     * Creates a new drasyl Node with the given <code>config</code>. The node is only being created,
     * it neither connects to the Overlay * Network, nor can send or receive messages. To do this
     * you have to call {@link #start()}.
     * <p>
     * Note: This is a blocking method, because when a node is started for the first time, its
     * identity must be created. This can take up to a minute because of the proof of work.
     *
     * @param config custom configuration used for this node
     */
    @SuppressWarnings({ "java:S2095" })
    protected DrasylNode(final DrasylConfig config) throws DrasylException {
        try {
            this.config = config;
            final IdentityManager identityManager = new IdentityManager(this.config);
            identityManager.loadOrCreateIdentity();
            this.identity = identityManager.getIdentity();
            this.peersManager = new PeersManager(this::onInternalEvent, identity);
            this.channelGroup = new PeerChannelGroup(this.config.getNetworkId(), identity);
            this.endpoints = new CopyOnWriteArraySet<>();
            this.acceptNewConnections = new AtomicBoolean();
            this.pipeline = new DrasylPipeline(this::onEvent, this.config, identity);
            this.started = new AtomicBoolean();
            pipeline.addFirst(SUPER_PEER_SINK_HANDLER, new SuperPeerMessageSinkHandler(channelGroup, peersManager));
            pipeline.addAfter(SUPER_PEER_SINK_HANDLER, DIRECT_CONNECTION_MESSAGE_SINK_HANDLER, new DirectConnectionMessageSinkHandler(channelGroup));
            pipeline.addAfter(DIRECT_CONNECTION_MESSAGE_SINK_HANDLER, LOOPBACK_MESSAGE_SINK_HANDLER, new LoopbackMessageSinkHandler(started, this.config.getNetworkId(), identity, peersManager, endpoints));
            this.components = new ArrayList<>();

            if (config.areDirectConnectionsEnabled()) {
                this.components.add(new DirectConnectionsManager(
                        config, identity, peersManager, pipeline, channelGroup,
                        LazyWorkerGroupHolder.INSTANCE,
                        acceptNewConnections::get, endpoints));
            }
            if (config.isIntraVmDiscoveryEnabled()) {
                this.components.add(new IntraVmDiscovery(identity.getPublicKey(),
                        peersManager, pipeline));
            }
            if (config.isSuperPeerEnabled()) {
                this.components.add(new SuperPeerClient(this.config, identity, peersManager,
                        pipeline, channelGroup, LazyWorkerGroupHolder.INSTANCE,
                        acceptNewConnections::get));
            }
            if (config.isServerEnabled()) {
                this.components.add(new Server(identity, pipeline, peersManager, this.config,
                        channelGroup, LazyWorkerGroupHolder.INSTANCE, LazyBossGroupHolder.INSTANCE,
                        endpoints, acceptNewConnections::get));
            }
            if (config.isLocalHostDiscoveryEnabled()) {
                this.components.add(new LocalHostDiscovery(this.config, identity.getPublicKey(),
                        peersManager, endpoints, pipeline));
            }
            if (config.isMonitoringEnabled()) {
                this.components.add(new Monitoring(config, peersManager,
                        identity.getPublicKey(), pipeline));
            }

            pipeline.addLast(OTHER_NETWORK_FILTER, new OtherNetworkFilter());

            this.pluginManager = new PluginManager(config, identity, pipeline);
            this.startSequence = new CompletableFuture<>();
            this.shutdownSequence = completedFuture(null);
        }
        catch (final ConfigException e) {
            throw new DrasylException("Couldn't load config: " + e.getMessage());
        }
    }

    protected DrasylNode(final DrasylConfig config,
                         final Identity identity,
                         final PeersManager peersManager,
                         final PeerChannelGroup channelGroup,
                         final Set<Endpoint> endpoints,
                         final AtomicBoolean acceptNewConnections,
                         final Pipeline pipeline,
                         final List<DrasylNodeComponent> components,
                         final PluginManager pluginManager,
                         final AtomicBoolean started,
                         final CompletableFuture<Void> startSequence,
                         final CompletableFuture<Void> shutdownSequence) {
        this.config = config;
        this.identity = identity;
        this.peersManager = peersManager;
        this.endpoints = endpoints;
        this.acceptNewConnections = acceptNewConnections;
        this.pipeline = pipeline;
        this.channelGroup = channelGroup;
        this.components = components;
        this.pluginManager = pluginManager;
        this.started = started;
        this.startSequence = startSequence;
        this.shutdownSequence = shutdownSequence;
    }

    /**
     * Returns the version of the node. If the version could not be read, <code>null</code> is
     * returned.
     *
     * @return the version of the node
     */
    public static String getVersion() {
        final Properties properties = new Properties();
        try {
            properties.load(DrasylNode.class.getClassLoader().getResourceAsStream("project.properties"));
            return properties.getProperty("version");
        }
        catch (final IOException e) {
            return null;
        }
    }

    /**
     * This method stops the shared threads ({@link EventLoopGroup}s), but only if none {@link
     * DrasylNode} is using them anymore.
     *
     * <p>
     * <b>This operation cannot be undone. After performing this operation, no new DrasylNodes can
     * be created!</b>
     * </p>
     */
    public static void irrevocablyTerminate() {
        if (INSTANCES.isEmpty()) {
            if (bossGroupCreated) {
                LazyBossGroupHolder.INSTANCE.shutdownGracefully().syncUninterruptibly();
            }

            if (workerGroupCreated) {
                LazyWorkerGroupHolder.INSTANCE.shutdownGracefully().syncUninterruptibly();
            }
        }
    }

    /**
     * Returns the {@link EventLoopGroup} that fits best to the current environment. Under Linux the
     * more performant {@link EpollEventLoopGroup} is returned.
     *
     * @return {@link EventLoopGroup} that fits best to the current environment
     */
    public static EventLoopGroup getBestEventLoop(final int poolSize) {
        if (Epoll.isAvailable()) {
            return new EpollEventLoopGroup(poolSize);
        }
        else {
            return new NioEventLoopGroup(poolSize);
        }
    }

    /**
     * Returns the {@link EventLoopGroup} that fits best to the current environment. Under Linux the
     * more performant {@link EpollEventLoopGroup} is returned.
     *
     * @return {@link EventLoopGroup} that fits best to the current environment
     */
    public static EventLoopGroup getBestEventLoop() {
        if (Epoll.isAvailable()) {
            return new EpollEventLoopGroup();
        }
        else {
            return new NioEventLoopGroup();
        }
    }

    /**
     * Sends <code>event</code> to the {@link Pipeline} and tells it information about the local
     * node, other peers, connections or incoming messages.
     * <br>
     * <b>This method should be used by all drasyl internal operations, so that the event can pass
     * the {@link org.drasyl.pipeline.Pipeline}</b>
     *
     * @param event the event
     */
    void onInternalEvent(final Event event) {
        pipeline.processInbound(event);
    }

    /**
     * Sends <code>event</code> to the application and tells it information about the local node,
     * other peers, connections or incoming messages.
     *
     * @param event the event
     */
    public abstract void onEvent(Event event);

    /**
     * Sends the content of {@code payload} to the identity {@code recipient}. Returns a failed
     * future with a {@link IllegalStateException} if the message could not be sent to the recipient
     * or a super peer. Important: Just because the future did not fail does not automatically mean
     * that the message could be delivered. Delivery confirmations must be implemented by the
     * application.
     *
     * <p>
     * <b>Note</b>: It is possible that the passed object cannot be serialized. In this case it is
     * not sent and the future is fulfilled with an exception. By default, drasyl allows the
     * serialization of Java's primitive types, as well as {@link String} and {@link Number}.
     * Further objects can be added on start via the {@link DrasylConfig} or on demand via {@link
     * HandlerContext#inboundValidator()} or {@link HandlerContext#outboundValidator()}. If the
     * {@link org.drasyl.pipeline.codec.DefaultCodec} does not support these objects, a custom
     * {@link Codec} can be added to the beginning of the {@link Pipeline}.
     * </p>
     *
     * @param recipient the recipient of a message as compressed public key
     * @param payload   the payload of a message
     * @return a completed future if the message was successfully processed, otherwise an
     * exceptionally future
     * @see org.drasyl.pipeline.codec.Codec
     * @see org.drasyl.pipeline.codec.DefaultCodec
     * @see org.drasyl.pipeline.codec.TypeValidator
     * @since 0.1.3-SNAPSHOT
     */
    public CompletableFuture<Void> send(final String recipient, final Object payload) {
        try {
            return send(CompressedPublicKey.of(recipient), payload);
        }
        catch (final CryptoException | IllegalArgumentException e) {
            return failedFuture(new DrasylException("Unable to parse recipient's public key: " + e.getMessage()));
        }
    }

    /**
     * Sends the content of {@code payload} to the identity {@code recipient}. Returns a failed
     * future with a {@link IllegalStateException} if the message could not be sent to the recipient
     * or a super peer. Important: Just because the future did not fail does not automatically mean
     * that the message could be delivered. Delivery confirmations must be implemented by the
     * application.
     *
     * <p>
     * <b>Note</b>: It is possible that the passed object cannot be serialized. In this case it is
     * not sent and the future is fulfilled with an exception. By default, drasyl allows the
     * serialization of Java's primitive types, as well as {@link String} and {@link Number}.
     * Further objects can be added on start via the {@link DrasylConfig} or on demand via {@link
     * HandlerContext#inboundValidator()} or {@link HandlerContext#outboundValidator()}. If the
     * {@link org.drasyl.pipeline.codec.DefaultCodec} does not support these objects, a custom
     * {@link Codec} can be added to the beginning of the {@link Pipeline}.
     * </p>
     *
     * @param recipient the recipient of a message
     * @param payload   the payload of a message
     * @return a completed future if the message was successfully processed, otherwise an
     * exceptionally future
     * @see org.drasyl.pipeline.codec.Codec
     * @see org.drasyl.pipeline.codec.DefaultCodec
     * @see org.drasyl.pipeline.codec.TypeValidator
     * @since 0.1.3-SNAPSHOT
     */
    public CompletableFuture<Void> send(final CompressedPublicKey recipient,
                                        final Object payload) {
        return pipeline.processOutbound(recipient, payload);
    }

    /**
     * Shut the drasyl node down.
     * <p>
     * If there is a connection to a Super Peer, our node will deregister from that Super Peer.
     * <p>
     * If the local server has been started, it will now be stopped.
     * <p>
     * This method does not stop the shared threads. To kill the shared threads, you have to call
     * the {@link #irrevocablyTerminate()} method.
     * <p>
     *
     * @return this method returns a future, which complements if all shutdown steps have been
     * completed.
     */
    public CompletableFuture<Void> shutdown() {
        if (startSequence.isDone() && started.compareAndSet(true, false)) {
            onInternalEvent(new NodeDownEvent(Node.of(identity, endpoints)));
            LOG.info("Shutdown drasyl Node with Identity '{}'...", identity);
            shutdownSequence = new CompletableFuture<>();
            pluginManager.beforeShutdown();

            startSequence.whenComplete((t, exp) -> getInstanceHeavy().scheduleDirect(() -> {
                rejectNewConnections();
                closeConnections();

                for (int i = components.size() - 1; i >= 0; i--) {
                    components.get(i).close();
                }

                onInternalEvent(new NodeNormalTerminationEvent(Node.of(identity, endpoints)));
                LOG.info("drasyl Node with Identity '{}' has shut down", identity);
                pluginManager.afterShutdown();
                INSTANCES.remove(DrasylNode.this);
                shutdownSequence.complete(null);
            }));
        }

        return shutdownSequence;
    }

    private void rejectNewConnections() {
        acceptNewConnections.set(false);
    }

    @SuppressWarnings({ "java:S1905" })
    private void closeConnections() {
        // send quit message to all peers and close connections
        final CompletableFuture<?>[] futures = channelGroup.stream().map(c -> FutureUtil.toFuture(c.writeAndFlush(new QuitMessage(config.getNetworkId(), identity.getPublicKey(), identity.getProofOfWork(), c.attr(ATTRIBUTE_PUBLIC_KEY).get(), REASON_SHUTTING_DOWN)))).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).thenRun(channelGroup::close);
    }

    /**
     * Start the drasyl node.
     * <p>
     * First, the identity of the node is loaded. If none exists, a new one is generated.
     * <p>
     * If activated, a local server is started. This allows other nodes to discover our node.
     * <p>
     * If a super peer has been configured, a client is started which connects to this super peer.
     * Our node uses the Super Peer to discover and communicate with other nodes.
     * <p>
     *
     * @return this method returns a future, which complements if all components necessary for the
     * operation have been started.
     */
    public CompletableFuture<Void> start() {
        if (started.compareAndSet(false, true)) {
            INSTANCES.add(this);
            LOG.info("Start drasyl Node v{}...", DrasylNode.getVersion());
            LOG.debug("The following configuration will be used: {}", config);
            startSequence = new CompletableFuture<>();
            shutdownSequence.whenComplete((t, ex) -> getInstanceHeavy().scheduleDirect(() -> {
                try {
                    pluginManager.beforeStart();
                    for (final DrasylNodeComponent component : components) {
                        component.open();
                    }
                    acceptNewConnections();

                    onInternalEvent(new NodeUpEvent(Node.of(identity, endpoints)));
                    LOG.info("drasyl Node with Identity '{}' has started", identity);
                    startSequence.complete(null);
                    pluginManager.afterStart();
                }
                catch (final DrasylException e) {
                    onInternalEvent(new NodeUnrecoverableErrorEvent(Node.of(identity, endpoints), e));
                    LOG.info("Could not start drasyl Node: {}", e.getMessage());
                    LOG.info("Stop all running components...");
                    pluginManager.beforeShutdown();

                    rejectNewConnections();
                    closeConnections();
                    for (int i = components.size() - 1; i >= 0; i--) {
                        components.get(i).close();
                    }

                    LOG.info("All components stopped");
                    started.set(false);
                    pluginManager.afterShutdown();
                    INSTANCES.remove(DrasylNode.this);
                    startSequence.completeExceptionally(e);
                }
            }));
        }

        return startSequence;
    }

    private void acceptNewConnections() {
        acceptNewConnections.set(true);
    }

    /**
     * Returns the {@link Pipeline} to allow users to add own handlers.
     *
     * @return the pipeline
     */
    public Pipeline pipeline() {
        return this.pipeline;
    }

    /**
     * Returns the {@link Identity} of this node.
     *
     * @return the {@link Identity} of this node
     */
    public Identity identity() {
        return identity;
    }

    private static class LazyWorkerGroupHolder {
        // https://github.com/netty/netty/issues/639#issuecomment-9263566
        static final EventLoopGroup INSTANCE = getBestEventLoop(Math.min(2,
                Math.max(2, Runtime.getRuntime().availableProcessors() * 2 / 3 - 2)));
        static final boolean LOCK = workerGroupCreated = true;

        private LazyWorkerGroupHolder() {
        }
    }

    private static class LazyBossGroupHolder {
        // https://github.com/netty/netty/issues/639#issuecomment-9263566
        static final EventLoopGroup INSTANCE = getBestEventLoop(2);
        static final boolean LOCK = bossGroupCreated = true;

        private LazyBossGroupHolder() {
        }
    }
}