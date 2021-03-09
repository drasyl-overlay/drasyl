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
package org.drasyl;

import io.netty.channel.EventLoopGroup;
import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.annotation.Beta;
import org.drasyl.annotation.NonNull;
import org.drasyl.annotation.Nullable;
import org.drasyl.event.Event;
import org.drasyl.event.Node;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityManager;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.DrasylPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.pipeline.serialization.MessageSerializer;
import org.drasyl.plugin.PluginManager;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.drasyl.util.NettyUtil.getBestEventLoopGroup;
import static org.drasyl.util.PlatformDependent.unsafeStaticFieldOffsetSupported;
import static org.drasyl.util.scheduler.DrasylSchedulerUtil.getInstanceHeavy;

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
@SuppressWarnings({ "java:S107", "java:S118" })
@Beta
public abstract class DrasylNode {
    private static final Logger LOG = LoggerFactory.getLogger(DrasylNode.class);
    private static final List<DrasylNode> INSTANCES;
    private static final boolean BOSS_GROUP_CREATED = false;
    private static String version;

    static {
        // https://github.com/netty/netty/issues/7817
        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
        INSTANCES = Collections.synchronizedList(new ArrayList<>());

        if (unsafeStaticFieldOffsetSupported()) {
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
            catch (final Exception e) { // NOSONAR
                LOG.debug("", e);
            }
        }
    }

    private final DrasylConfig config;
    private final Identity identity;
    private final PeersManager peersManager;
    private final Pipeline pipeline;
    private final PluginManager pluginManager;
    private final AtomicReference<CompletableFuture<Void>> startFuture;
    private final AtomicReference<CompletableFuture<Void>> shutdownFuture;
    private final Scheduler scheduler;

    /**
     * Creates a new drasyl Node. The node is only being created, it neither connects to the Overlay
     * Network, nor can send or receive messages. To do this you have to call {@link #start()}.
     * <p>
     * Note: This is a blocking method, because when a node is started for the first time, its
     * identity must be created. This can take up to a minute because of the proof of work.
     *
     * @throws DrasylException       if identity could not be loaded or created
     * @throws DrasylConfigException if config is invalid
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
     * @throws NullPointerException if {@code config} is {@code null}
     * @throws DrasylException      if identity could not be loaded or created
     */
    @SuppressWarnings({ "java:S2095" })
    protected DrasylNode(final DrasylConfig config) throws DrasylException {
        try {
            this.config = requireNonNull(config);
            final IdentityManager identityManager = new IdentityManager(this.config);
            identityManager.loadOrCreateIdentity();
            this.identity = identityManager.getIdentity();
            this.peersManager = new PeersManager(this::onInternalEvent, identity);
            this.pipeline = new DrasylPipeline(this::onEvent, this.config, identity, peersManager, LazyBossGroupHolder.INSTANCE);
            this.pluginManager = new PluginManager(config, identity, pipeline);
            this.startFuture = new AtomicReference<>();
            this.shutdownFuture = new AtomicReference<>(completedFuture(null));
            this.scheduler = getInstanceHeavy();
        }
        catch (final IOException e) {
            throw new DrasylException("Couldn't load or create identity", e);
        }
    }

    protected DrasylNode(final DrasylConfig config,
                         final Identity identity,
                         final PeersManager peersManager,
                         final Pipeline pipeline,
                         final PluginManager pluginManager,
                         final AtomicReference<CompletableFuture<Void>> startFuture,
                         final AtomicReference<CompletableFuture<Void>> shutdownFuture,
                         final Scheduler scheduler) {
        this.config = config;
        this.identity = identity;
        this.peersManager = peersManager;
        this.pipeline = pipeline;
        this.pluginManager = pluginManager;
        this.startFuture = startFuture;
        this.shutdownFuture = shutdownFuture;
        this.scheduler = scheduler;
    }

    /**
     * Returns the version of the node. If the version could not be read, {@code null} is returned.
     *
     * @return the version of the node. If the version could not be read, {@code null} is returned
     */
    @Nullable
    public static String getVersion() {
        if (version == null) {
            final InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("META-INF/org.drasyl.versions.properties");
            if (inputStream != null) {
                try {
                    final Properties properties = new Properties();
                    properties.load(inputStream);
                    version = properties.getProperty("version");
                }
                catch (final IOException e) {
                    LOG.debug("Unable to read properties file.", e);
                }
            }
        }

        return version;
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
        if (INSTANCES.isEmpty() && BOSS_GROUP_CREATED) {
            LazyBossGroupHolder.INSTANCE.shutdownGracefully().syncUninterruptibly();
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
    public abstract void onEvent(@NonNull Event event);

    /**
     * Sends the content of {@code payload} to the identity {@code recipient}. Returns a failed
     * future with a {@link IllegalStateException} if the message could not be sent to the recipient
     * or a super peer. Important: Just because the future did not fail does not automatically mean
     * that the message could be delivered. Delivery confirmations must be implemented by the
     * application.
     *
     * <p>
     * <b>Note</b>: It is possible that the passed object cannot be serialized. In this case it is
     * not sent and the future is fulfilled with an exception. Serializable objects can be added on
     * start via the {@link DrasylConfig} or on demand via {@link HandlerContext#inboundSerialization()}
     * or {@link HandlerContext#outboundSerialization()}.
     * </p>
     *
     * @param recipient the recipient of a message as compressed public key
     * @param payload   the payload of a message
     * @return a completed future if the message was successfully processed, otherwise an
     * exceptionally future
     * @see org.drasyl.pipeline.Handler
     * @see MessageSerializer
     * @since 0.1.3-SNAPSHOT
     */
    @NonNull
    public CompletableFuture<Void> send(@NonNull final String recipient,
                                        final Object payload) {
        try {
            return send(CompressedPublicKey.of(recipient), payload);
        }
        catch (final IllegalArgumentException e) {
            return failedFuture(new DrasylException("Recipient does not conform to a valid public key.", e));
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
     * not sent and the future is fulfilled with an exception. Serializable objects can be added on
     * start via the {@link DrasylConfig} or on demand via {@link HandlerContext#inboundSerialization()}
     * or {@link HandlerContext#outboundSerialization()}.
     * </p>
     *
     * @param recipient the recipient of a message
     * @param payload   the payload of a message
     * @return a completed future if the message was successfully processed, otherwise an
     * exceptionally future
     * @see org.drasyl.pipeline.Handler
     * @see MessageSerializer
     * @since 0.1.3-SNAPSHOT
     */
    @NonNull
    public CompletableFuture<Void> send(@Nullable final CompressedPublicKey recipient,
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
    @NonNull
    public CompletableFuture<Void> shutdown() {
        final CompletableFuture<Void> newShutdownFuture = new CompletableFuture<>();
        final CompletableFuture<Void> previousShutdownFuture = shutdownFuture.compareAndExchange(null, newShutdownFuture);
        if (previousShutdownFuture == null) {
            LOG.info("Shutdown drasyl Node with Identity '{}'...", identity);
            scheduler.scheduleDirect(() -> {
                synchronized (startFuture) {
                    onInternalEvent(NodeDownEvent.of(Node.of(identity))).whenComplete((result, e) -> {
                        if (e != null) {
                            LOG.error("Node faced error on shutdown (NodeDownEvent):", e);
                        }
                        pluginManager.beforeShutdown();
                        onInternalEvent(NodeNormalTerminationEvent.of(Node.of(identity))).whenComplete((result2, e2) -> {
                            if (e2 != null) {
                                LOG.error("Node faced error on shutdown (NodeNormalTerminationEvent):", e2);
                            }
                            LOG.info("drasyl Node with Identity '{}' has shut down", identity);
                            pluginManager.afterShutdown();
                            INSTANCES.remove(DrasylNode.this);
                            newShutdownFuture.complete(null);
                            startFuture.set(null);
                        });
                    });
                }
            });

            return newShutdownFuture;
        }
        else {
            return previousShutdownFuture;
        }
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
    @NonNull
    public CompletableFuture<Void> start() {
        final CompletableFuture<Void> newStartFuture = new CompletableFuture<>();
        final CompletableFuture<Void> previousStartFuture = startFuture.compareAndExchange(null, newStartFuture);
        if (previousStartFuture == null) {
            LOG.info("Start drasyl Node v{}...", DrasylNode.getVersion());
            LOG.debug("The following configuration will be used: {}", config);
            scheduler.scheduleDirect(() -> {
                synchronized (startFuture) {
                    INSTANCES.add(this);
                    pluginManager.beforeStart();
                    onInternalEvent(NodeUpEvent.of(Node.of(identity))).whenComplete((result, e) -> {
                        if (e == null) {
                            LOG.info("drasyl Node with Identity '{}' has started", identity);
                            pluginManager.afterStart();
                            newStartFuture.complete(null);
                            shutdownFuture.set(null);
                        }
                        else {
                            LOG.warn("Could not start drasyl Node:", e);
                            pluginManager.beforeShutdown();
                            onInternalEvent(NodeUnrecoverableErrorEvent.of(Node.of(identity), e)).whenComplete((result2, e2) -> {
                                if (e2 != null) {
                                    LOG.error("Node faced error '{}' on startup, which caused it to shut down all already started components. This again resulted in an error: {}", e.getMessage(), e2.getMessage());
                                }

                                pluginManager.afterShutdown();
                                INSTANCES.remove(DrasylNode.this);
                                newStartFuture.completeExceptionally(new Exception("Node start failed:", e));
                                startFuture.set(null);
                            });
                        }
                    });
                }
            });

            return newStartFuture;
        }
        else {
            return previousStartFuture;
        }
    }

    /**
     * Returns the {@link Pipeline} to allow users to add own handlers.
     *
     * @return the pipeline
     */
    @NonNull
    public Pipeline pipeline() {
        return this.pipeline;
    }

    /**
     * Returns the {@link Identity} of this node.
     *
     * @return the {@link Identity} of this node
     */
    @NonNull
    public Identity identity() {
        return identity;
    }

    private static final class LazyBossGroupHolder {
        // https://github.com/netty/netty/issues/639#issuecomment-9263566
        static final EventLoopGroup INSTANCE = getBestEventLoopGroup(2);

        private LazyBossGroupHolder() {
        }
    }
}
