package org.drasyl.peer.connection.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.util.DrasylFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Used by {@link org.drasyl.peer.connection.direct.DirectConnectionsManager} to establish a direct
 * connection to another peer.
 */
@SuppressWarnings({ "java:S107" })
public class DirectClient extends AbstractClient {
    private static final Logger LOG = LoggerFactory.getLogger(DirectClient.class);
    private final BooleanSupplier directConnectionDemand;
    private final Runnable onFailure;

    public DirectClient(DrasylConfig config,
                        Identity identity,
                        PeersManager peersManager,
                        Messenger messenger,
                        PeerChannelGroup channelGroup,
                        EventLoopGroup workerGroup,
                        Consumer<Event> eventConsumer,
                        CompressedPublicKey serverPublicKey,
                        Supplier<Set<Endpoint>> endpointsSupplier,
                        BooleanSupplier directConnectionDemand,
                        Runnable onFailure,
                        BooleanSupplier acceptNewConnectionsSupplier) {
        super(
                config.getDirectConnectionsRetryDelays(),
                workerGroup,
                endpointsSupplier,
                acceptNewConnectionsSupplier,
                identity,
                messenger,
                peersManager,
                config,
                channelGroup,
                config.getDirectConnectionsIdleRetries(),
                config.getDirectConnectionsIdleTimeout(),
                config.getDirectConnectionsHandshakeTimeout(),
                eventConsumer,
                false,
                serverPublicKey,
                config.getDirectConnectionsChannelInitializer()
        );
        this.directConnectionDemand = directConnectionDemand;
        this.onFailure = onFailure;
    }

    DirectClient(List<Duration> retryDelays,
                 EventLoopGroup workerGroup,
                 Supplier<Set<Endpoint>> endpointsSupplier,
                 AtomicBoolean opened,
                 BooleanSupplier acceptNewConnectionsSupplier,
                 AtomicInteger nextEndpointPointer,
                 AtomicInteger nextRetryDelayPointer,
                 DrasylFunction<Endpoint, Bootstrap, ClientException> bootstrapSupplier,
                 Channel channel,
                 BooleanSupplier directConnectionDemand,
                 Runnable onFailure) {
        super(retryDelays, workerGroup, endpointsSupplier, opened, acceptNewConnectionsSupplier, nextEndpointPointer, nextRetryDelayPointer, bootstrapSupplier, channel);
        this.directConnectionDemand = directConnectionDemand;
        this.onFailure = onFailure;
    }

    /**
     * Should only make a new connection attempt if there is still a demand for it.
     *
     * @return if a retry should be made
     */
    @Override
    protected boolean shouldRetry() {
        return directConnectionDemand.getAsBoolean() && super.shouldRetry();
    }

    /**
     * Call <code>onFailure</code> if the client can permanently not establish a connection
     * anymore.
     */
    @Override
    protected void failed() {
        super.failed();

        getLogger().debug("Permanently unable to connect to peer. Close and remove this client.");

        close();
        onFailure.run();
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
}