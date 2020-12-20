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

import com.google.common.cache.CacheBuilder;
import com.google.protobuf.MessageLite;
import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.DrasylConfig;
import org.drasyl.crypto.CryptoException;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeerInformation;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.codec.ObjectHolder;
import org.drasyl.pipeline.message.ApplicationMessage;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
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
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static java.lang.Boolean.TRUE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.remote.protocol.Protocol.MessageType.ACKNOWLEDGEMENT;
import static org.drasyl.remote.protocol.Protocol.MessageType.APPLICATION;
import static org.drasyl.remote.protocol.Protocol.MessageType.DISCOVERY;
import static org.drasyl.remote.protocol.Protocol.MessageType.UNITE;
import static org.drasyl.util.LoggingUtil.sanitizeLogArg;

@SuppressWarnings({ "java:S110", "java:S1192" })
public class UdpDiscoveryHandler extends SimpleDuplexHandler<IntermediateEnvelope<? extends MessageLite>, ApplicationMessage, Address> {
    public static final String UDP_DISCOVERY_HANDLER = "UDP_DISCOVERY_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(UdpDiscoveryHandler.class);
    private static final Object path = UdpDiscoveryHandler.class;
    private final Map<MessageId, OpenPing> openPingsCache;
    private final Map<Pair<CompressedPublicKey, CompressedPublicKey>, Boolean> uniteAttemptsCache;
    private final Map<CompressedPublicKey, Peer> peers;
    private final Set<CompressedPublicKey> directConnectionPeers;
    private Disposable heartbeatDisposable;

    public UdpDiscoveryHandler(final DrasylConfig config) {
        openPingsCache = CacheBuilder.newBuilder()
                .maximumSize(config.getRemotePingMaxPeers())
                .expireAfterWrite(config.getRemotePingTimeout())
                .<MessageId, OpenPing>build()
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
        peers = new HashMap<>();
    }

    UdpDiscoveryHandler(final Map<MessageId, OpenPing> openPingsCache,
                        final Map<Pair<CompressedPublicKey, CompressedPublicKey>, Boolean> uniteAttemptsCache,
                        final Map<CompressedPublicKey, Peer> peers,
                        final Set<CompressedPublicKey> directConnectionPeers) {
        this.openPingsCache = openPingsCache;
        this.uniteAttemptsCache = uniteAttemptsCache;
        this.directConnectionPeers = directConnectionPeers;
        this.peers = peers;
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
        }

        // passthrough event
        ctx.fireEventTriggered(event, future);
    }

    synchronized void startHeartbeat(final HandlerContext ctx) {
        if (heartbeatDisposable == null) {
            LOG.debug("Start heartbeat scheduler");
            heartbeatDisposable = ctx.scheduler()
                    .schedulePeriodicallyDirect(() -> doHeartbeat(ctx), 0, ctx.config().getRemotePingInterval().toMillis(), MILLISECONDS);
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
        pingSuperPeer(ctx);
        pingDirectConnectionPeers(ctx);
    }

    /**
     * This method removes stale peers from the peer list, that not respond to ping messages.
     *
     * @param ctx the handler context
     */
    private void removeStalePeers(final HandlerContext ctx) {
        // check lastContactTimes
        final CompressedPublicKey superPeerKey = ctx.config().getRemoteSuperPeerEndpoint().getPublicKey();
        new HashMap<>(peers).forEach(((publicKey, peer) -> {
            if (!peer.hasControlTraffic(ctx.config())) {
                LOG.debug("Last contact from {} is {}ms ago. Remove peer.", () -> publicKey, () -> System.currentTimeMillis() - peer.getLastInboundControlTrafficTime());
                if (publicKey.equals(superPeerKey)) {
                    ctx.peersManager().unsetSuperPeerAndRemovePath(path);
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
     * If the node has configured a Super Peer, a ping message is sent to it.
     *
     * @param ctx handler's context
     */
    private void pingSuperPeer(final HandlerContext ctx) {
        if (ctx.config().isRemoteSuperPeerEnabled()) {
            sendPing(ctx, ctx.config().getRemoteSuperPeerEndpoint().getPublicKey(),
                    InetSocketAddressWrapper.of(
                            new InetSocketAddress(ctx.config().getRemoteSuperPeerEndpoint().getHost(),
                                    ctx.config().getRemoteSuperPeerEndpoint().getPort())),
                    true, new CompletableFuture<>());
        }
    }

    /**
     * Sends ping messages to all peers with whom a direct connection should be kept open. Removes
     * peers that have not had application-level communication with you for a while.
     *
     * @param ctx handler's context
     */
    private void pingDirectConnectionPeers(final HandlerContext ctx) {
        new HashSet<>(directConnectionPeers).forEach(publicKey -> {
            final Peer peer = peers.get(publicKey);
            final InetSocketAddressWrapper address = peer.getAddress();
            if (address != null && peer.hasApplicationTraffic(ctx.config())) {
                sendPing(ctx, publicKey, address, false, new CompletableFuture<>());
            }
            // remove trivial communications, that does not send any user generated messages
            else {
                LOG.debug("Last application communication to {} is {}ms ago. Remove peer.", () -> publicKey, () -> System.currentTimeMillis() - peer.getLastApplicationTrafficTime());
                ctx.peersManager().removeChildrenAndPath(publicKey, path);
                directConnectionPeers.remove(publicKey);
            }
        });
    }

    private void sendPing(final HandlerContext ctx,
                          final CompressedPublicKey publicKey,
                          final InetSocketAddressWrapper recipientAddress,
                          final boolean isChildrenJoin,
                          final CompletableFuture<Void> future) {
        final int networkId = ctx.config().getNetworkId();
        final CompressedPublicKey sender = ctx.identity().getPublicKey();
        final ProofOfWork proofOfWork = ctx.identity().getProofOfWork();

        final IntermediateEnvelope<Discovery> messageEnvelope = IntermediateEnvelope.discovery(networkId, sender, proofOfWork, publicKey, isChildrenJoin ? System.currentTimeMillis() : 0);
        openPingsCache.put(messageEnvelope.getId(), new OpenPing(recipientAddress, isChildrenJoin));
        LOG.trace("Send {} to {}", messageEnvelope, recipientAddress);
        ctx.write(recipientAddress, messageEnvelope, future);
    }

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final Address recipient,
                                final ApplicationMessage msg,
                                final CompletableFuture<Void> future) {
        // record communication to keep active connections alive
        if (directConnectionPeers.contains(msg.getRecipient())) {
            final Peer peer = peers.computeIfAbsent(msg.getRecipient(), key -> new Peer());
            peer.applicationTrafficOccurred();
        }

        if (recipient instanceof CompressedPublicKey) {
            final IntermediateEnvelope<Application> remoteMessageEnvelope = IntermediateEnvelope.application(ctx.config().getNetworkId(), ctx.identity().getPublicKey(), ctx.identity().getProofOfWork(), msg.getRecipient(), msg.getContent().getClazzAsString(), msg.getContent().getObject());
            processMessage(ctx, (CompressedPublicKey) recipient, remoteMessageEnvelope, future);
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
     */
    private void processMessage(final HandlerContext ctx,
                                final CompressedPublicKey recipient,
                                final IntermediateEnvelope<? extends MessageLite> msg,
                                final CompletableFuture<Void> future) {
        final Peer recipientPeer = peers.get(recipient);
        final CompressedPublicKey superPeerKey = ctx.peersManager().getSuperPeerKey();
        final Peer superPeerPeer;
        if (superPeerKey != null) {
            superPeerPeer = peers.get(superPeerKey);
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
                LOG.trace("Relay message from {} to {}.", msg.getSender(), recipient);

                if (shouldTryUnite(msg.getSender(), msg.getRecipient())) {
                    ctx.scheduler().scheduleDirect(() -> sendUnites(ctx, msg.getSender(), msg.getRecipient(), recipientSocketAddress, senderSocketAddress));
                }
            }

            LOG.trace("Send message to {} to {}.", recipient, recipientSocketAddress);
            ctx.write(recipientSocketAddress, msg, future);
        }
        else if (superPeerPeer != null) {
            final InetSocketAddressWrapper superPeerSocketAddress = superPeerPeer.getAddress();
            LOG.trace("No connection to {}. Send message to super peer.", recipient);
            ctx.write(superPeerSocketAddress, msg, future);
        }
        else {
            // passthrough message
            ctx.write(recipient, msg, future);
        }
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
    private void sendUnites(final HandlerContext ctx,
                            final CompressedPublicKey senderKey,
                            final CompressedPublicKey recipientKey,
                            final InetSocketAddressWrapper recipient,
                            final InetSocketAddressWrapper sender) {
        // send recipient's information to sender
        final IntermediateEnvelope<Unite> senderRendezvousEnvelope = IntermediateEnvelope.unite(ctx.config().getNetworkId(), ctx.identity().getPublicKey(), ctx.identity().getProofOfWork(), senderKey, recipientKey, recipient.getAddress());
        LOG.trace("Send {} to {}", senderRendezvousEnvelope, sender);
        ctx.write(sender, senderRendezvousEnvelope, new CompletableFuture<>());

        // send sender's information to recipient
        final IntermediateEnvelope<Unite> recipientRendezvousEnvelope = IntermediateEnvelope.unite(ctx.config().getNetworkId(), ctx.identity().getPublicKey(), ctx.identity().getProofOfWork(), recipientKey, senderKey, sender.getAddress());
        LOG.trace("Send {} to {}", recipientRendezvousEnvelope, recipient);
        ctx.write(recipient, recipientRendezvousEnvelope, new CompletableFuture<>());
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final IntermediateEnvelope<? extends MessageLite> envelope,
                               final CompletableFuture<Void> future) {
        requireNonNull(envelope);
        requireNonNull(sender);

        try {
            // This message is for us and we will fully decode it
            if (envelope.getRecipient().equals(ctx.identity().getPublicKey())) {
                handleMessage(ctx, sender, envelope, future);
            }
            else {
                if (!ctx.config().isRemoteSuperPeerEnabled()) {
                    processMessage(ctx, envelope.getRecipient(), envelope, future);
                }
                else {
                    LOG.debug("We're not a super peer. Message {} from {} for relaying was dropped.", envelope, sender);
                }
            }
        }
        catch (final IOException | CryptoException | IllegalArgumentException e) {
            LOG.warn("Unable to deserialize '{}': {}", () -> sanitizeLogArg(envelope.getByteBuf()), e::getMessage);
            future.completeExceptionally(new Exception("Message could not be deserialized.", e));
            ReferenceCountUtil.safeRelease(envelope);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleMessage(final HandlerContext ctx,
                               final Address sender,
                               final IntermediateEnvelope<? extends MessageLite> envelope,
                               final CompletableFuture<Void> future) throws IOException, CryptoException {
        try {
            if (sender instanceof InetSocketAddressWrapper && envelope.getPrivateHeader().getType() == DISCOVERY) {
                handlePing(ctx, (InetSocketAddressWrapper) sender, (IntermediateEnvelope<Discovery>) envelope, future);
            }
            else if (sender instanceof InetSocketAddressWrapper && envelope.getPrivateHeader().getType() == ACKNOWLEDGEMENT) {
                handlePong(ctx, (InetSocketAddressWrapper) sender, (IntermediateEnvelope<Acknowledgement>) envelope, future);
            }
            else if (envelope.getPrivateHeader().getType() == UNITE && ctx.config().getRemoteSuperPeerEndpoint().getPublicKey().equals(envelope.getSender())) {
                handleUnite(ctx, (IntermediateEnvelope<Unite>) envelope, future);
            }
            else if (envelope.getPrivateHeader().getType() == APPLICATION) {
                handleApplication(ctx, sender, (IntermediateEnvelope<Application>) envelope, future);
            }
            else {
                envelope.retain();
                // passthrough message
                ctx.fireRead(sender, envelope, future);
            }
        }
        finally {
            ReferenceCountUtil.safeRelease(envelope);
        }
    }

    private void handlePing(final HandlerContext ctx,
                            final InetSocketAddressWrapper senderSocketAddress,
                            final IntermediateEnvelope<Discovery> envelope,
                            final CompletableFuture<Void> future) throws IOException, CryptoException {
        final CompressedPublicKey sender = requireNonNull(CompressedPublicKey.of(envelope.getPublicHeader().getSender().toByteArray()));
        final MessageId id = requireNonNull(MessageId.of(envelope.getPublicHeader().getId().toByteArray()));
        final boolean childrenJoin = envelope.getBodyAndRelease().getChildrenTime() > 0;
        LOG.trace("Got {} from {}", envelope, senderSocketAddress);
        final Peer peer = peers.computeIfAbsent(sender, key -> new Peer());
        peer.setAddress(senderSocketAddress);
        peer.inboundControlTrafficOccurred();

        if (childrenJoin) {
            peer.inboundPongOccurred();
            // store peer information
            if (LOG.isDebugEnabled() && !ctx.peersManager().getChildrenKeys().contains(sender) && !ctx.peersManager().getPeer(sender).second().contains(path)) {
                LOG.debug("PING! Add {} as children", sender);
            }
            ctx.peersManager().setPeerInformationAndAddPathAndChildren(sender, PeerInformation.of(), path);
        }

        // reply with pong
        final int networkId = ctx.config().getNetworkId();
        final CompressedPublicKey myPublicKey = ctx.identity().getPublicKey();
        final ProofOfWork myProofOfWork = ctx.identity().getProofOfWork();
        final IntermediateEnvelope<Acknowledgement> responseEnvelope = IntermediateEnvelope.acknowledgement(networkId, myPublicKey, myProofOfWork, sender, id);
        LOG.trace("Send {} to {}", responseEnvelope, senderSocketAddress);
        ctx.write(senderSocketAddress, responseEnvelope, future);
    }

    private void handlePong(final HandlerContext ctx,
                            final InetSocketAddressWrapper senderSocketAddress,
                            final IntermediateEnvelope<Acknowledgement> envelope,
                            final CompletableFuture<Void> future) throws IOException, CryptoException {
        final MessageId correspondingId = requireNonNull(MessageId.of(envelope.getBodyAndRelease().getCorrespondingId().toByteArray()));
        final CompressedPublicKey sender = requireNonNull(CompressedPublicKey.of(envelope.getPublicHeader().getSender().toByteArray()));
        LOG.trace("Got {} from {}", envelope, senderSocketAddress);
        final OpenPing openPing = openPingsCache.remove(correspondingId);
        if (openPing != null) {
            final Peer peer = peers.computeIfAbsent(sender, key -> new Peer());
            peer.setAddress(senderSocketAddress);
            peer.inboundControlTrafficOccurred();
            peer.inboundPongOccurred();
            if (openPing.isChildrenJoin()) {
                // store peer information
                if (LOG.isDebugEnabled() && !ctx.peersManager().getChildrenKeys().contains(sender) && !ctx.peersManager().getPeer(sender).second().contains(path)) {
                    LOG.debug("PONG! Add {} as super peer", sender);
                }
                ctx.peersManager().setPeerInformationAndAddPathAndSetSuperPeer(sender, PeerInformation.of(), path);
            }
            else {
                // store peer information
                if (LOG.isDebugEnabled() && !ctx.peersManager().getPeer(sender).second().contains(path)) {
                    LOG.debug("PONG! Add {} as peer", sender);
                }
                ctx.peersManager().setPeerInformationAndAddPath(sender, PeerInformation.of(), path);
            }
        }
        future.complete(null);
    }

    private void handleUnite(final HandlerContext ctx,
                             final IntermediateEnvelope<Unite> envelope,
                             final CompletableFuture<Void> future) throws IOException, CryptoException {
        final CompressedPublicKey publicKey = requireNonNull(CompressedPublicKey.of(envelope.getBodyAndRelease().getPublicKey().toByteArray()));
        final InetSocketAddress address = new InetSocketAddress(envelope.getBodyAndRelease().getAddress(), UnsignedShort.of(envelope.getBodyAndRelease().getPort().toByteArray()).getValue());
        LOG.trace("Got {}", envelope);
        final InetSocketAddressWrapper socketAddress = new InetSocketAddressWrapper(address);
        final Peer peer = peers.computeIfAbsent(publicKey, key -> new Peer());
        peer.setAddress(socketAddress);
        peer.inboundControlTrafficOccurred();
        peer.applicationTrafficOccurred();
        directConnectionPeers.add(publicKey);
        sendPing(ctx, publicKey, socketAddress, false, future);
    }

    private void handleApplication(final HandlerContext ctx,
                                   final Address sender,
                                   final IntermediateEnvelope<Application> envelope,
                                   final CompletableFuture<Void> future) throws IOException {
        if (directConnectionPeers.contains(envelope.getSender())) {
            final Peer peer = peers.computeIfAbsent(envelope.getSender(), key -> new Peer());
            peer.applicationTrafficOccurred();
        }

        // convert to ApplicationMessage
        final ApplicationMessage applicationMessage = new ApplicationMessage(envelope.getSender(), envelope.getRecipient(), ObjectHolder.of(requireNonNull(envelope.getBodyAndRelease().getType()), requireNonNull(envelope.getBodyAndRelease().getPayload().toByteArray())));
        ctx.fireRead(sender, applicationMessage, future);
    }

    static class Peer {
        private InetSocketAddressWrapper address;
        private long lastInboundControlTrafficTime;
        private long lastInboundPongTime;

        Peer(final InetSocketAddressWrapper address,
             final long lastInboundControlTrafficTime,
             final long lastInboundPongTime,
             final long lastApplicationTrafficTime) {
            this.address = address;
            this.lastInboundControlTrafficTime = lastInboundControlTrafficTime;
            this.lastInboundPongTime = lastInboundPongTime;
            this.lastApplicationTrafficTime = lastApplicationTrafficTime;
        }

        private long lastApplicationTrafficTime;

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

        public void inboundPongOccurred() {
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
    }

    public static class OpenPing {
        private final InetSocketAddressWrapper address;
        private final boolean isChildrenJoin;

        public OpenPing(final InetSocketAddressWrapper address, final boolean isChildrenJoin) {
            this.address = address;
            this.isChildrenJoin = isChildrenJoin;
        }

        public InetSocketAddressWrapper getAddress() {
            return address;
        }

        public boolean isChildrenJoin() {
            return isChildrenJoin;
        }

        @Override
        public int hashCode() {
            return Objects.hash(address, isChildrenJoin);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final OpenPing openPing = (OpenPing) o;
            return isChildrenJoin == openPing.isChildrenJoin &&
                    Objects.equals(address, openPing.address);
        }

        @Override
        public String toString() {
            return "OpenPing{" +
                    "address=" + address +
                    ", isChildrenJoin=" + isChildrenJoin +
                    '}';
        }
    }
}
