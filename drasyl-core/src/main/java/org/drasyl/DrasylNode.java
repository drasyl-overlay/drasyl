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
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.DrasylPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.codec.Codec;
import org.drasyl.plugin.PluginManager;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
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
    private static volatile boolean bossGroupCreated = false;

    static {
        // https://github.com/netty/netty/issues/7817
        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
        INSTANCES = Collections.synchronizedList(new ArrayList<>());

        // dirty fix from Stack Overflow: https://stackoverflow.com/questions/46454995/how-to-hide-warning-illegal-reflective-access-in-java-9-without-jvm-argument/53517025#53517025
        try {
            final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            final Field field = unsafeClass.getDeclaredField("theUnsafe");
            field.setAccessible(true); // NOSONAR
            final Object unsafe = field.get(null);

            final Method putObjectVolatile = unsafeClass.getDeclaredMethod("putObjectVolatile", Object.class, long.class, Object.class);
            final Method staticFieldOffset = unsafeClass.getDeclaredMethod("staticFieldOffset", Field.class);

            final Class<?> loggerClass = Class.forName("jdk.internal.module.IllegalAccessLogger");
            final Field loggerField = loggerClass.getDeclaredField("logger");
            final Long offset = (Long) staticFieldOffset.invoke(unsafe, loggerField);
            putObjectVolatile.invoke(unsafe, loggerClass, offset, null);
        }
        catch (final Exception ignored) {
            // ignore
        }
    }

    private final DrasylConfig config;
    private final Identity identity;
    private final PeersManager peersManager;
    private final Set<Endpoint> endpoints;
    private final AtomicBoolean acceptNewConnections;
    private final Pipeline pipeline;
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
            this.endpoints = new CopyOnWriteArraySet<>();
            this.acceptNewConnections = new AtomicBoolean();
            this.started = new AtomicBoolean();
            this.pipeline = new DrasylPipeline(this::onEvent, this.config, identity, peersManager, started, LazyBossGroupHolder.INSTANCE);
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
                         final Set<Endpoint> endpoints,
                         final AtomicBoolean acceptNewConnections,
                         final Pipeline pipeline,
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
        if (INSTANCES.isEmpty() && bossGroupCreated) {
            LazyBossGroupHolder.INSTANCE.shutdownGracefully().syncUninterruptibly();
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
     * Sends <code>event</code> to the {@link Pipeline} and tells it information about the local
     * node, other peers, connections or incoming messages.
     * <br>
     * <b>This method should be used by all drasyl internal operations, so that the event can pass
     * the {@link org.drasyl.pipeline.Pipeline}</b>
     *
     * @param event the event
     */
    CompletableFuture<Void> onInternalEvent(final Event event) {
        return pipeline.processInbound(event);
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
        if (startSequence.isDone() && !startSequence.isCompletedExceptionally() && started.compareAndSet(true, false)) {
            onInternalEvent(new NodeDownEvent(Node.of(identity)));
            LOG.info("Shutdown drasyl Node with Identity '{}'...", identity);
            shutdownSequence = new CompletableFuture<>();
            pluginManager.beforeShutdown();

            startSequence.whenComplete((t, exp) -> getInstanceHeavy().scheduleDirect(() -> {
                rejectNewConnections();
                onInternalEvent(new NodeNormalTerminationEvent(Node.of(identity))).join();

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
                pluginManager.beforeStart();
                acceptNewConnections();
                try {
                    onInternalEvent(new NodeUpEvent(Node.of(identity))).get();
                    LOG.info("drasyl Node with Identity '{}' has started", identity);
                    startSequence.complete(null);
                    pluginManager.afterStart();
                }
                catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                catch (final ExecutionException e) {
                    LOG.warn("Could not start drasyl Node: {}", e.getCause().getMessage());
                    pluginManager.beforeShutdown();
                    onInternalEvent(new NodeUnrecoverableErrorEvent(Node.of(identity), e.getCause())).join();
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

    private static class LazyBossGroupHolder {
        // https://github.com/netty/netty/issues/639#issuecomment-9263566
        static final EventLoopGroup INSTANCE = getBestEventLoop(2);
        static final boolean LOCK = bossGroupCreated = true;

        private LazyBossGroupHolder() {
        }
    }
}