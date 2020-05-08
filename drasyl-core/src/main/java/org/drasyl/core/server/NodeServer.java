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
package org.drasyl.core.server;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.drasyl.core.common.messages.IMessage;
import org.drasyl.core.common.messages.Message;
import org.drasyl.core.common.messages.UserAgentMessage;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.node.DrasylNodeConfig;
import org.drasyl.core.node.Messenger;
import org.drasyl.core.node.PeerInformation;
import org.drasyl.core.node.PeersManager;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.node.identity.IdentityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@SuppressWarnings({ "squid:S00107" })
public class NodeServer implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NodeServer.class);
    public final EventLoopGroup workerGroup;
    public final EventLoopGroup bossGroup;
    public final ServerBootstrap serverBootstrap;
    private final List<Runnable> beforeCloseListeners;
    private final IdentityManager identityManager;
    private final PeersManager peersManager;
    private final DrasylNodeConfig config;
    private final Messenger messenger;
    private final AtomicBoolean opened;
    private Channel serverChannel;
    private NodeServerBootstrap nodeServerBootstrap;
    private int actualPort;
    private Set<URI> actualEndpoints;

    /**
     * Starts a node server for forwarding messages to child peers.<br> Default Port: 22527
     */
    public NodeServer(IdentityManager identityManager,
                      Messenger messenger,
                      PeersManager peersManager) throws DrasylException {
        this(identityManager, messenger, peersManager, ConfigFactory.load());
    }

    public NodeServer(IdentityManager identityManager,
                      Messenger messenger,
                      PeersManager peersManager,
                      Config config) throws DrasylException {
        this(identityManager, messenger, peersManager, new DrasylNodeConfig(config));
    }

    /**
     * Node server for forwarding messages to child peers.
     *
     * @param identityManager the identity manager
     * @param messenger       the messenger object
     * @param peersManager    the peers manager
     * @param config          config that should be used
     */
    public NodeServer(IdentityManager identityManager,
                      Messenger messenger,
                      PeersManager peersManager,
                      DrasylNodeConfig config) throws NodeServerException {
        this(identityManager, messenger, peersManager, config,
                null, new ServerBootstrap(),
                new NioEventLoopGroup(Math.min(1, ForkJoinPool.commonPool().getParallelism() - 1)), new NioEventLoopGroup(1), new ArrayList<>(),
                new CompletableFuture<>(), new CompletableFuture<>(), null, new AtomicBoolean(false), -1, new HashSet<>());

        overrideUA();

        nodeServerBootstrap = new NodeServerBootstrap(this, serverBootstrap, config);
    }

    NodeServer(IdentityManager identityManager,
               Messenger messenger,
               PeersManager peersManager,
               DrasylNodeConfig config,
               Channel serverChannel,
               ServerBootstrap serverBootstrap,
               EventLoopGroup workerGroup,
               EventLoopGroup bossGroup,
               List<Runnable> beforeCloseListeners,
               CompletableFuture<Void> startedFuture,
               CompletableFuture<Void> stoppedFuture,
               NodeServerBootstrap nodeServerBootstrap,
               AtomicBoolean opened,
               int actualPort,
               Set<URI> actualEndpoints) {
        this.identityManager = identityManager;
        this.peersManager = peersManager;
        this.config = config;
        this.serverChannel = serverChannel;
        this.serverBootstrap = serverBootstrap;
        this.workerGroup = workerGroup;
        this.bossGroup = bossGroup;
        this.beforeCloseListeners = beforeCloseListeners;
        this.nodeServerBootstrap = nodeServerBootstrap;
        this.opened = opened;
        this.messenger = messenger;
        this.actualPort = actualPort;
        this.actualEndpoints = actualEndpoints;
    }

    /**
     * Overrides the default UA of the {@link IMessage Message} object.
     */
    @SuppressWarnings({ "squid:S2696" })
    private void overrideUA() {
        UserAgentMessage.userAgentGenerator = () -> UserAgentMessage.defaultUserAgentGenerator.get() + " " + config.getUserAgent();
    }

    public void send(Message message) throws DrasylException {
        messenger.send(message);
    }

    /**
     * @return the peers manager
     */
    public PeersManager getPeersManager() {
        return peersManager;
    }

    /**
     * @return the entry points
     */
    public Set<URI> getEntryPoints() {
        return actualEndpoints;
    }

    /**
     * @return the config
     */
    public DrasylNodeConfig getConfig() {
        return config;
    }

    @SuppressWarnings({ "java:S1144" })
    private void beforeClose(Runnable listener) {
        beforeCloseListeners.add(listener);
    }

    EventLoopGroup getBossGroup() {
        return bossGroup;
    }

    EventLoopGroup getWorkerGroup() {
        return workerGroup;
    }

    public boolean isOpen() {
        return opened.get();
    }

    public IdentityManager getMyIdentity() {
        return identityManager;
    }

    /**
     * Starts the relay server.
     */
    public void open() throws NodeServerException {
        if (opened.compareAndSet(false, true)) {
            serverChannel = nodeServerBootstrap.getChannel();

            InetSocketAddress socketAddress = (InetSocketAddress) serverChannel.localAddress();
            actualPort = socketAddress.getPort();
            actualEndpoints = config.getServerEndpoints().stream()
                    .map(a -> overridePort(URI.create(a), getPort()))
                    .collect(Collectors.toSet());
        }
    }

    /**
     * This method sets the port in <code>uri</code> to <code>port</code> and returns the resulting
     * URI.
     *
     * @param uri
     * @param port
     * @return
     */
    private URI overridePort(URI uri, int port) {
        try {
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), port, uri.getPath(), uri.getQuery(), uri.getFragment());
        }
        catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * Returns the actual bind port used by this server.
     */
    public int getPort() {
        return actualPort;
    }

    /**
     * Adds a Runnable to the beforeCloseListeners List.
     *
     * @param runnable runnable that should be executed before close
     */
    public void addBeforeCloseListener(Runnable runnable) {
        beforeCloseListeners.add(runnable);
    }

    /**
     * Removes a Runnable from the beforeCloseListeners List.
     *
     * @param runnable runnable that should be removed
     */
    public void removeBeforeCloseListener(Runnable runnable) {
        beforeCloseListeners.remove(runnable);
    }

    /**
     * Closes the server socket and all open client sockets.
     */
    @Override
    public void close() {
        if (opened.compareAndSet(true, false)) {
            beforeCloseListeners.forEach(Runnable::run);

            Map<Identity, PeerInformation> localPeers = new HashMap<>(peersManager.getPeers());
            localPeers.keySet().retainAll(peersManager.getChildren());

            localPeers.forEach((id, peer) -> peer.getConnections().forEach(con -> {
                if (con != null) {
                    con.close();
                }
            }));

            bossGroup.shutdownGracefully().syncUninterruptibly();
            workerGroup.shutdownGracefully().syncUninterruptibly();
            if (serverChannel != null && serverChannel.isOpen()) {
                serverChannel.closeFuture().syncUninterruptibly();
            }
        }
    }
}
