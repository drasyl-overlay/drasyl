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

import com.google.common.cache.CacheBuilder;
import com.google.protobuf.MessageLite;
import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.Endpoint;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.serialization.SerializedApplicationMessage;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.drasyl.remote.protocol.AddressedIntermediateEnvelope;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.remote.protocol.MessageId;
import org.drasyl.remote.protocol.Protocol.Acknowledgement;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.remote.protocol.Protocol.Discovery;
import org.drasyl.remote.protocol.Protocol.Unite;
import org.drasyl.util.Pair;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.UnsignedShort;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.lang.Boolean.TRUE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.remote.protocol.Protocol.MessageType.ACKNOWLEDGEMENT;
import static org.drasyl.remote.protocol.Protocol.MessageType.APPLICATION;
import static org.drasyl.remote.protocol.Protocol.MessageType.DISCOVERY;
import static org.drasyl.remote.protocol.Protocol.MessageType.UNITE;
import static org.drasyl.util.LoggingUtil.sanitizeLogArg;
import static org.drasyl.util.RandomUtil.randomLong;

/**
 * This handler performs the following tasks, which help to communicate with nodes located in other
 * networks:
 * <ul>
 * <li>Joins one or more super peers or acts itself as a super peer (super peers act as registries of available nodes on the network. they can be used as message relays and help to traverse NATs).</li>
 * <li>Tracks which nodes are being communicated with and tries to establish direct connections to these nodes with the help of a super peer.</li>
 * <li>Routes messages to the recipient. If no route is known, the message is relayed to a super peer (our default gateway).</li>
 * </ul>
 */
@SuppressWarnings({ "java:S110", "java:S1192" })
public class InternetDiscoveryHandler extends SimpleDuplexHandler<AddressedIntermediateEnvelope<? extends MessageLite>, SerializedApplicationMessage, Address> {
    public static final String INTERNET_DISCOVERY_HANDLER = "INTERNET_DISCOVERY_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(InternetDiscoveryHandler.class);
    private static final Object path = InternetDiscoveryHandler.class;
    private final Map<MessageId, Ping> openPingsCache;
    private final Map<Pair<CompressedPublicKey, CompressedPublicKey>, Boolean> uniteAttemptsCache;
    private final Map<CompressedPublicKey, Peer> peers;
    private final Set<CompressedPublicKey> directConnectionPeers;
    private final Set<CompressedPublicKey> superPeers;
    private Disposable heartbeatDisposable;
    private CompressedPublicKey bestSuperPeer;

    public InternetDiscoveryHandler(final DrasylConfig config) {
        openPingsCache = CacheBuilder.newBuilder()
                .maximumSize(config.getRemotePingMaxPeers())
                .expireAfterWrite(config.getRemotePingTimeout())
                .<MessageId, Ping>build()
                .asMap();
        directConnectionPeers = new HashSet<>();
        if (config.getRemoteUniteMinInterval().toMillis() > 0) {
            uniteAttemptsCache = CacheBuilder.newBuilder()
                    .maximumSize(1_000)
                    .expireAfterWrite(config.getRemoteUniteMinInterval())
                    .<Pair<CompressedPublicKey, CompressedPublicKey>, Boolean>build()
                    .asMap();
        }
        else {
            uniteAttemptsCache = null;
        }
        peers = new ConcurrentHashMap<>();
        superPeers = config.getRemoteSuperPeerEndpoints().stream().map(Endpoint::getPublicKey).collect(Collectors.toSet());
    }

    @SuppressWarnings("java:S2384")
    InternetDiscoveryHandler(final Map<MessageId, Ping> openPingsCache,
                             final Map<Pair<CompressedPublicKey, CompressedPublicKey>, Boolean> uniteAttemptsCache,
                             final Map<CompressedPublicKey, Peer> peers,
                             final Set<CompressedPublicKey> directConnectionPeers,
                             final Set<CompressedPublicKey> superPeers,
                             final CompressedPublicKey bestSuperPeer) {
        this.openPingsCache = openPingsCache;
        this.uniteAttemptsCache = uniteAttemptsCache;
        this.directConnectionPeers = directConnectionPeers;
        this.peers = peers;
        this.superPeers = superPeers;
        this.bestSuperPeer = bestSuperPeer;
    }

    @Override
    public void eventTriggered(final HandlerContext ctx,
                               final Event event,
                               final CompletableFuture<Void> future) {
        if (event instanceof NodeUpEvent) {
            startHeartbeat(ctx);
        }
        else if (event instanceof NodeUnrecoverableErrorEvent || event instanceof NodeDownEvent) {
            stopHeartbeat();
            openPingsCache.clear();
            uniteAttemptsCache.clear();
            removeAllPeers(ctx);
        }

        // passthrough event
        ctx.fireEventTriggered(event, future);
    }

    synchronized void startHeartbeat(final HandlerContext ctx) {
        if (heartbeatDisposable == null) {
            LOG.debug("Start heartbeat scheduler");
            final long pingInterval = ctx.config().getRemotePingInterval().toMillis();
            heartbeatDisposable = ctx.independentScheduler()
                    .schedulePeriodicallyDirect(() -> doHeartbeat(ctx), randomLong(pingInterval), pingInterval, MILLISECONDS);
        }
    }

    synchronized void stopHeartbeat() {
        if (heartbeatDisposable != null) {
            LOG.debug("Stop heartbeat scheduler");
            heartbeatDisposable.dispose();
            heartbeatDisposable = null;
        }
    }

    /**
     * This method sends ping messages to super peer and direct connection peers.
     *
     * @param ctx handler's context
     */
    void doHeartbeat(final HandlerContext ctx) {
        removeStalePeers(ctx);
        pingSuperPeers(ctx);
        pingDirectConnectionPeers(ctx);
    }

    /**
     * This method removes stale peers from the peer list, that not respond to ping messages.
     *
     * @param ctx the handler context
     */
    private void removeStalePeers(final HandlerContext ctx) {
        // check lastContactTimes
        new HashMap<>(peers).forEach(((publicKey, peer) -> {
            if (!peer.hasControlTraffic(ctx.config())) {
                LOG.debug("Last contact from {} is {}ms ago. Remove peer.", () -> publicKey, () -> System.currentTimeMillis() - peer.getLastInboundControlTrafficTime());
                if (superPeers.contains(publicKey)) {
                    ctx.peersManager().removeSuperPeerAndPath(publicKey, path);
                }
                else {
                    ctx.peersManager().removeChildrenAndPath(publicKey, path);
                }
                peers.remove(publicKey);
                directConnectionPeers.remove(publicKey);
            }
        }));
    }

    /**
     * If the node has configured super peers, a ping message is sent to them.
     *
     * @param ctx handler's context
     */
    private void pingSuperPeers(final HandlerContext ctx) {
        if (ctx.config().isRemoteSuperPeerEnabled()) {
            for (final Endpoint endpoint : ctx.config().getRemoteSuperPeerEndpoints()) {
                sendPing(ctx, endpoint.getPublicKey(), new InetSocketAddressWrapper(endpoint.getHost(), endpoint.getPort()), new CompletableFuture<>());
            }
        }
    }

    /**
     * Sends ping messages to all peers with whom a direct connection should be kept open. Removes
     * peers that have not had application-level communication with you for a while.
     *
     * @param ctx handler's context
     */
    private void pingDirectConnectionPeers(final HandlerContext ctx) {
        for (final CompressedPublicKey publicKey : new HashSet<>(directConnectionPeers)) {
            final Peer peer = peers.get(publicKey);
            final InetSocketAddressWrapper address = peer.getAddress();
            if (address != null && peer.hasApplicationTraffic(ctx.config())) {
                sendPing(ctx, publicKey, address, new CompletableFuture<>());
            }
            // remove trivial communications, that does not send any user generated messages
            else {
                LOG.debug("Last application communication to {} is {}ms ago. Remove peer.", () -> publicKey, () -> System.currentTimeMillis() - peer.getLastApplicationTrafficTime());
                ctx.peersManager().removeChildrenAndPath(publicKey, path);
                directConnectionPeers.remove(publicKey);
            }
        }
    }

    private void removeAllPeers(final HandlerContext ctx) {
        new HashMap<>(peers).forEach(((publicKey, peer) -> {
            if (superPeers.contains(publicKey)) {
                ctx.peersManager().removeSuperPeerAndPath(publicKey, path);
            }
            else {
                ctx.peersManager().removeChildrenAndPath(publicKey, path);
            }
            peers.remove(publicKey);
            directConnectionPeers.remove(publicKey);
        }));
    }

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final Address recipient,
                                final SerializedApplicationMessage msg,
                                final CompletableFuture<Void> future) {
        // record communication to keep active connections alive
        if (directConnectionPeers.contains(msg.getRecipient())) {
            final Peer peer = peers.computeIfAbsent(msg.getRecipient(), key -> new Peer());
            peer.applicationTrafficOccurred();
        }

        if (recipient instanceof CompressedPublicKey) {
            IntermediateEnvelope<Application> remoteMessageEnvelope = null;
            try {
                remoteMessageEnvelope = IntermediateEnvelope.application(ctx.config().getNetworkId(), ctx.identity().getPublicKey(), ctx.identity().getProofOfWork(), msg.getRecipient(), msg.getType(), msg.getContent());
                processMessage(ctx, (CompressedPublicKey) recipient, remoteMessageEnvelope, future);
            }
            catch (final IOException e) {
                // should never occur as we build the message ourselves
                future.completeExceptionally(e);
                ReferenceCountUtil.safeRelease(remoteMessageEnvelope);
            }
        }
        else {
            // passthrough message
            ctx.write(recipient, msg, future);
        }
    }

    /**
     * This method tries to find the cheapest path to the {@code recipient}. If we're a relay, we
     * additionally try to initiate a rendezvous between the two corresponding nodes.
     *
     * @param ctx       the handler context
     * @param recipient the recipient of the message
     * @param msg       the message
     * @param future    the future
     * @throws IOException if public header of {@code msg} cannot be read
     */
    private void processMessage(final HandlerContext ctx,
                                final CompressedPublicKey recipient,
                                final IntermediateEnvelope<? extends MessageLite> msg,
                                final CompletableFuture<Void> future) throws IOException {
        final Peer recipientPeer = peers.get(recipient);

        final Peer superPeerPeer;
        if (bestSuperPeer != null) {
            superPeerPeer = peers.get(bestSuperPeer);
        }
        else {
            superPeerPeer = null;
        }

        if (recipientPeer != null && recipientPeer.getAddress() != null && recipientPeer.isReachable(ctx.config())) {
            final InetSocketAddressWrapper recipientSocketAddress = recipientPeer.getAddress();
            final Peer senderPeer = peers.get(msg.getSender());

            // rendezvous? I'm a super peer?
            if (senderPeer != null && superPeerPeer == null && senderPeer.getAddress() != null) {
                final InetSocketAddressWrapper senderSocketAddress = senderPeer.getAddress();
                final CompressedPublicKey msgSender = msg.getSender();
                final CompressedPublicKey msgRecipient = msg.getRecipient();
                LOG.trace("Relay message from {} to {}.", msgSender, recipient);

                if (shouldTryUnite(msgSender, msgRecipient)) {
                    ctx.independentScheduler().scheduleDirect(() -> sendUnites(ctx, msgSender, msgRecipient, recipientSocketAddress, senderSocketAddress));
                }
            }

            LOG.trace("Send message to {} to {}.", recipient, recipientSocketAddress);
            ctx.write(recipientSocketAddress, new AddressedIntermediateEnvelope<>(null, recipientSocketAddress, msg), future);
        }
        else if (superPeerPeer != null) {
            final InetSocketAddressWrapper superPeerSocketAddress = superPeerPeer.getAddress();
            LOG.trace("No connection to {}. Send message to super peer.", recipient);
            ctx.write(superPeerSocketAddress, new AddressedIntermediateEnvelope<>(null, superPeerSocketAddress, msg), future);
        }
        else {
            // passthrough message
            ctx.write(recipient, msg, future);
        }
    }

    /**
     * This method initiates a unite between the {@code sender} and {@code recipient}, by sending
     * both the required information to establish a direct (P2P) connection.
     *
     * @param ctx          the handler context
     * @param senderKey    the sender's public key
     * @param recipientKey the recipient's public key
     * @param recipient    the recipient socket address
     * @param sender       the sender socket address
     */
    private static void sendUnites(final HandlerContext ctx,
                                   final CompressedPublicKey senderKey,
                                   final CompressedPublicKey recipientKey,
                                   final InetSocketAddressWrapper recipient,
                                   final InetSocketAddressWrapper sender) {
        // send recipient's information to sender
        final IntermediateEnvelope<Unite> senderRendezvousEnvelope = IntermediateEnvelope.unite(ctx.config().getNetworkId(), ctx.identity().getPublicKey(), ctx.identity().getProofOfWork(), senderKey, recipientKey, recipient);
        final AddressedIntermediateEnvelope<Unite> addressedSenderRendezvousEnvelope = new AddressedIntermediateEnvelope<>(null, sender, senderRendezvousEnvelope);
        LOG.trace("Send {} to {}", senderRendezvousEnvelope, sender);
        ctx.write(sender, addressedSenderRendezvousEnvelope, new CompletableFuture<>());

        // send sender's information to recipient
        final IntermediateEnvelope<Unite> recipientRendezvousEnvelope = IntermediateEnvelope.unite(ctx.config().getNetworkId(), ctx.identity().getPublicKey(), ctx.identity().getProofOfWork(), recipientKey, senderKey, sender);
        final AddressedIntermediateEnvelope<Unite> addressedSecipientRendezvousEnvelope = new AddressedIntermediateEnvelope<>(null, recipient, recipientRendezvousEnvelope);
        LOG.trace("Send {} to {}", recipientRendezvousEnvelope, recipient);
        ctx.write(recipient, addressedSecipientRendezvousEnvelope, new CompletableFuture<>());
    }

    private synchronized boolean shouldTryUnite(final CompressedPublicKey sender,
                                                final CompressedPublicKey recipient) {
        final Pair<CompressedPublicKey, CompressedPublicKey> key;
        if (sender.hashCode() > recipient.hashCode()) {
            key = Pair.of(sender, recipient);
        }
        else {
            key = Pair.of(recipient, sender);
        }
        return uniteAttemptsCache != null && uniteAttemptsCache.putIfAbsent(key, TRUE) == null;
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final AddressedIntermediateEnvelope<? extends MessageLite> envelope,
                               final CompletableFuture<Void> future) {
        requireNonNull(envelope);
        requireNonNull(sender);

        try {
            // This message is for us and we will fully decode it
            if (envelope.getContent().getRecipient().equals(ctx.identity().getPublicKey())) {
                handleMessage(ctx, sender, envelope, future);
            }
            else {
                if (!ctx.config().isRemoteSuperPeerEnabled()) {
                    processMessage(ctx, envelope.getContent().getRecipient(), envelope.getContent(), future);
                }
                else if (LOG.isDebugEnabled()) {
                    LOG.debug("We're not a super peer. Message `{}` from `{}` (`{}`) to `{}` for relaying was dropped.", envelope, envelope.getContent().getSender(), sender, envelope.getContent().getRecipient());
                }
            }
        }
        catch (final IOException e) {
            LOG.warn("Unable to deserialize '{}'.", () -> sanitizeLogArg(envelope.getContent()), () -> e);
            future.completeExceptionally(new Exception("Message could not be deserialized.", e));
            ReferenceCountUtil.safeRelease(envelope);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleMessage(final HandlerContext ctx,
                               final Address sender,
                               final AddressedIntermediateEnvelope<? extends MessageLite> envelope,
                               final CompletableFuture<Void> future) throws IOException {
        if (envelope.getContent().getPrivateHeader().getType() == DISCOVERY) {
            handlePing(ctx, (AddressedIntermediateEnvelope<Discovery>) envelope, future);
        }
        else if (envelope.getContent().getPrivateHeader().getType() == ACKNOWLEDGEMENT) {
            handlePong(ctx, (AddressedIntermediateEnvelope<Acknowledgement>) envelope, future);
        }
        else if (envelope.getContent().getPrivateHeader().getType() == UNITE && superPeers.contains(envelope.getContent().getSender())) {
            handleUnite(ctx, (AddressedIntermediateEnvelope<Unite>) envelope, future);
        }
        else if (envelope.getContent().getPrivateHeader().getType() == APPLICATION) {
            handleApplication(ctx, (AddressedIntermediateEnvelope<Application>) envelope, future);
        }
        else {
            envelope.getContent().retain();
            // passthrough message
            ctx.fireRead(sender, envelope, future);
        }
    }

    private void handlePing(final HandlerContext ctx,
                            final AddressedIntermediateEnvelope<Discovery> envelope,
                            final CompletableFuture<Void> future) throws IOException {
        final CompressedPublicKey sender = requireNonNull(CompressedPublicKey.of(envelope.getContent().getPublicHeader().getSender().toByteArray()));
        final MessageId id = requireNonNull(MessageId.of(envelope.getContent().getPublicHeader().getId()));
        final Discovery body = envelope.getContent().getBodyAndRelease();
        final boolean childrenJoin = body.getChildrenTime() > 0;
        LOG.trace("Got {} from {}", envelope.getContent(), envelope.getSender());
        final Peer peer = peers.computeIfAbsent(sender, key -> new Peer());
        peer.setAddress(envelope.getSender());
        peer.inboundControlTrafficOccurred();

        if (childrenJoin) {
            peer.inboundPingOccurred();
            // store peer information
            if (LOG.isDebugEnabled() && !ctx.peersManager().getChildren().contains(sender) && !ctx.peersManager().getPaths(sender).contains(path)) {
                LOG.debug("PING! Add {} as children", sender);
            }
            ctx.peersManager().addPathAndChildren(sender, path);
        }

        // reply with pong
        final int networkId = ctx.config().getNetworkId();
        final CompressedPublicKey myPublicKey = ctx.identity().getPublicKey();
        final ProofOfWork myProofOfWork = ctx.identity().getProofOfWork();
        final IntermediateEnvelope<Acknowledgement> responseEnvelope = IntermediateEnvelope.acknowledgement(networkId, myPublicKey, myProofOfWork, sender, id);
        LOG.trace("Send {} to {}", responseEnvelope, envelope.getSender());
        final AddressedIntermediateEnvelope<Acknowledgement> addressedResponseEnvelope = new AddressedIntermediateEnvelope<>(null, envelope.getSender(), responseEnvelope);
        ctx.write(envelope.getSender(), addressedResponseEnvelope, future);
    }

    private void handlePong(final HandlerContext ctx,
                            final AddressedIntermediateEnvelope<Acknowledgement> envelope,
                            final CompletableFuture<Void> future) throws IOException {
        final Acknowledgement body = envelope.getContent().getBodyAndRelease();
        final MessageId correspondingId = requireNonNull(MessageId.of(body.getCorrespondingId()));
        final CompressedPublicKey sender = requireNonNull(CompressedPublicKey.of(envelope.getContent().getPublicHeader().getSender().toByteArray()));
        LOG.trace("Got {} from {}", envelope.getContent(), envelope.getSender());
        final Ping ping = openPingsCache.remove(correspondingId);
        if (ping != null) {
            final Peer peer = peers.computeIfAbsent(sender, key -> new Peer());
            peer.setAddress(envelope.getSender());
            peer.inboundControlTrafficOccurred();
            peer.inboundPongOccurred(ping);
            if (superPeers.contains(envelope.getContent().getSender())) {
                LOG.trace("Latency to super peer `{}` ({}): {}ms", () -> sender, peer.getAddress()::getHostName, peer::getLatency);
                determineBestSuperPeer();

                // store peer information
                if (LOG.isDebugEnabled() && !ctx.peersManager().getChildren().contains(sender) && !ctx.peersManager().getPaths(sender).contains(path)) {
                    LOG.debug("PONG! Add {} as super peer", sender);
                }
                ctx.peersManager().addPathAndSuperPeer(sender, path);
            }
            else {
                // store peer information
                if (LOG.isDebugEnabled() && !ctx.peersManager().getPaths(sender).contains(path)) {
                    LOG.debug("PONG! Add {} as peer", sender);
                }
                ctx.peersManager().addPath(sender, path);
            }
        }
        future.complete(null);
    }

    private synchronized void determineBestSuperPeer() {
        long bestLatency = Long.MAX_VALUE;
        CompressedPublicKey newBestSuperPeer = null;
        for (final CompressedPublicKey superPeer : superPeers) {
            final Peer superPeerPeer = peers.get(superPeer);
            if (superPeerPeer != null) {
                final long latency = superPeerPeer.getLatency();
                if (bestLatency > latency) {
                    bestLatency = latency;
                    newBestSuperPeer = superPeer;
                }
            }
        }

        if (LOG.isDebugEnabled() && !Objects.equals(bestSuperPeer, newBestSuperPeer)) {
            LOG.debug("New best super peer ({}ms)! Replace `{}` with `{}`", bestLatency, bestSuperPeer, newBestSuperPeer);
        }

        bestSuperPeer = newBestSuperPeer;
    }

    private void handleUnite(final HandlerContext ctx,
                             final AddressedIntermediateEnvelope<Unite> envelope,
                             final CompletableFuture<Void> future) throws IOException {
        final Unite body = envelope.getContent().getBodyAndRelease();
        final CompressedPublicKey publicKey = requireNonNull(CompressedPublicKey.of(body.getPublicKey().toByteArray()));
        final InetSocketAddressWrapper address = new InetSocketAddressWrapper(body.getAddress(), UnsignedShort.of(body.getPort().toByteArray()).getValue());
        LOG.trace("Got {}", envelope.getContent());
        final Peer peer = peers.computeIfAbsent(publicKey, key -> new Peer());
        peer.setAddress(address);
        peer.inboundControlTrafficOccurred();
        peer.applicationTrafficOccurred();
        directConnectionPeers.add(publicKey);
        sendPing(ctx, publicKey, address, future);
    }

    private void handleApplication(final HandlerContext ctx,
                                   final AddressedIntermediateEnvelope<Application> envelope,
                                   final CompletableFuture<Void> future) throws IOException {
        if (directConnectionPeers.contains(envelope.getContent().getSender())) {
            final Peer peer = peers.computeIfAbsent(envelope.getContent().getSender(), key -> new Peer());
            peer.applicationTrafficOccurred();
        }

        // convert to ApplicationMessage
        final Application application = envelope.getContent().getBodyAndRelease();
        final SerializedApplicationMessage applicationMessage = new SerializedApplicationMessage(envelope.getContent().getSender(), envelope.getContent().getRecipient(), application.getType(), application.getPayload().toByteArray());
        ctx.fireRead(envelope.getSender(), applicationMessage, future);
    }

    private void sendPing(final HandlerContext ctx,
                          final CompressedPublicKey recipient,
                          final InetSocketAddressWrapper recipientAddress,
                          final CompletableFuture<Void> future) {
        final int networkId = ctx.config().getNetworkId();
        final CompressedPublicKey sender = ctx.identity().getPublicKey();
        final ProofOfWork proofOfWork = ctx.identity().getProofOfWork();

        final boolean isChildrenJoin = superPeers.contains(recipient);
        IntermediateEnvelope<Discovery> messageEnvelope = null;
        try {
            messageEnvelope = IntermediateEnvelope.discovery(networkId, sender, proofOfWork, recipient, isChildrenJoin ? System.currentTimeMillis() : 0);
            openPingsCache.put(messageEnvelope.getId(), new Ping(recipientAddress));
            LOG.trace("Send {} to {}", messageEnvelope, recipientAddress);
            final AddressedIntermediateEnvelope<Discovery> addressedMessageEnvelope = new AddressedIntermediateEnvelope<>(null, recipientAddress, messageEnvelope);
            ctx.write(recipientAddress, addressedMessageEnvelope, future);
        }
        catch (final IOException e) {
            // should never occur as we build the message ourselves
            future.completeExceptionally(e);
            ReferenceCountUtil.safeRelease(messageEnvelope);
        }
    }

    @SuppressWarnings("java:S2972")
    static class Peer {
        private InetSocketAddressWrapper address;
        private long lastInboundControlTrafficTime;
        private long lastInboundPongTime;
        private long lastApplicationTrafficTime;
        private long lastOutboundPingTime;

        Peer(final InetSocketAddressWrapper address,
             final long lastInboundControlTrafficTime,
             final long lastInboundPongTime,
             final long lastApplicationTrafficTime) {
            this.address = address;
            this.lastInboundControlTrafficTime = lastInboundControlTrafficTime;
            this.lastInboundPongTime = lastInboundPongTime;
            this.lastApplicationTrafficTime = lastApplicationTrafficTime;
        }

        public Peer() {
        }

        public InetSocketAddressWrapper getAddress() {
            return address;
        }

        public void setAddress(final InetSocketAddressWrapper address) {
            this.address = address;
        }

        /**
         * Returns the time when we last received a message from this peer. This includes all
         * message types ({@link Discovery}, {@link Acknowledgement}, {@link Application}, {@link
         * Unite}, etc.)
         */
        public long getLastInboundControlTrafficTime() {
            return lastInboundControlTrafficTime;
        }

        public void inboundControlTrafficOccurred() {
            lastInboundControlTrafficTime = System.currentTimeMillis();
        }

        public void inboundPongOccurred(final Ping ping) {
            lastInboundPongTime = System.currentTimeMillis();
            lastOutboundPingTime = Math.max(ping.getPingTime(), lastOutboundPingTime);
        }

        public void inboundPingOccurred() {
            lastInboundPongTime = System.currentTimeMillis();
        }

        /**
         * Returns the time when we last sent or received a application-level message to or from
         * this peer. This includes only message type {@link Application}
         */
        public long getLastApplicationTrafficTime() {
            return lastApplicationTrafficTime;
        }

        public void applicationTrafficOccurred() {
            lastApplicationTrafficTime = System.currentTimeMillis();
        }

        public boolean hasApplicationTraffic(final DrasylConfig config) {
            return lastApplicationTrafficTime >= System.currentTimeMillis() - config.getRemotePingCommunicationTimeout().toMillis();
        }

        public boolean hasControlTraffic(final DrasylConfig config) {
            return lastInboundControlTrafficTime >= System.currentTimeMillis() - config.getRemotePingTimeout().toMillis();
        }

        public boolean isReachable(final DrasylConfig config) {
            return lastInboundPongTime >= System.currentTimeMillis() - config.getRemotePingTimeout().toMillis();
        }

        public long getLatency() {
            return lastInboundPongTime - lastOutboundPingTime;
        }
    }

    @SuppressWarnings("java:S2972")
    public static class Ping {
        private final InetSocketAddressWrapper address;
        private final long pingTime;

        public Ping(final InetSocketAddressWrapper address) {
            this.address = requireNonNull(address);
            pingTime = System.currentTimeMillis();
        }

        public InetSocketAddressWrapper getAddress() {
            return address;
        }

        @Override
        public int hashCode() {
            return Objects.hash(address);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Ping ping = (Ping) o;
            return Objects.equals(address, ping.address);
        }

        @Override
        public String toString() {
            return "OpenPing{" +
                    "address=" + address +
                    '}';
        }

        public long getPingTime() {
            return pingTime;
        }
    }
}
