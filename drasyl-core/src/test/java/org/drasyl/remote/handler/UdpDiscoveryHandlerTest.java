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

import com.google.protobuf.MessageLite;
import io.netty.util.ReferenceCounted;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.crypto.CryptoException;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.pipeline.message.ApplicationMessage;
import org.drasyl.remote.handler.UdpDiscoveryHandler.OpenPing;
import org.drasyl.remote.handler.UdpDiscoveryHandler.Peer;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.remote.protocol.MessageId;
import org.drasyl.remote.protocol.Protocol.Acknowledgement;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.remote.protocol.Protocol.Discovery;
import org.drasyl.remote.protocol.Protocol.Unite;
import org.drasyl.util.Pair;
import org.drasyl.util.ReferenceCountUtil;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.drasyl.remote.protocol.Protocol.MessageType.ACKNOWLEDGEMENT;
import static org.drasyl.remote.protocol.Protocol.MessageType.APPLICATION;
import static org.drasyl.remote.protocol.Protocol.MessageType.UNITE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UdpDiscoveryHandlerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private TypeValidator inboundValidator;
    @Mock
    private TypeValidator outboundValidator;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Map<MessageId, OpenPing> openPingsCache;
    @Mock
    private PeersManager peersManager;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Set<CompressedPublicKey> rendezvousPeers;
    @Mock
    private Map<Pair<CompressedPublicKey, CompressedPublicKey>, Boolean> uniteAttemptsCache;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Map<CompressedPublicKey, Peer> peers;

    @Test
    void shouldPassthroughAllOtherEvents(@Mock final NodeEvent event) {
        final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, peers, rendezvousPeers);
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
        final TestObserver<Event> inboundEvents = pipeline.inboundEvents().test();

        pipeline.processInbound(event).join();

        inboundEvents.awaitCount(1).assertValueCount(1);
        inboundEvents.assertValue(m -> m instanceof NodeEvent);
        pipeline.close();
    }

    @Nested
    class DoHeartbeat {
        @Test
        void shouldStartHeartbeatingOnNodeUpEvent(@Mock final NodeUpEvent event) {
            when(config.getRemotePingInterval()).thenReturn(ofSeconds(5));

            final UdpDiscoveryHandler handler = spy(new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, peers, rendezvousPeers));
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

            pipeline.processInbound(event).join();

            verify(handler).startHeartbeat(any());
            pipeline.close();
        }

        @Test
        void shouldStopHeartbeatingOnNodeUnrecoverableErrorEvent(@Mock final NodeUnrecoverableErrorEvent event) {
            final UdpDiscoveryHandler handler = spy(new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, peers, rendezvousPeers));
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

            pipeline.processInbound(event).join();

            verify(handler).stopHeartbeat();
            pipeline.close();
        }

        @Test
        void shouldStopHeartbeatingOnNodeDownEvent(@Mock final NodeDownEvent event) {
            final UdpDiscoveryHandler handler = spy(new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, peers, rendezvousPeers));
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

            pipeline.processInbound(event).join();

            verify(handler).stopHeartbeat();
            pipeline.close();
        }

        @Test
        void shouldReplyWithAcknowledgmentMessageToDiscoveryMessage(@Mock(answer = RETURNS_DEEP_STUBS) final Peer peer,
                                                                    @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddressWrapper address) throws CryptoException {
            final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
            final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
            final IntermediateEnvelope<Discovery> discoveryMessage = IntermediateEnvelope.discovery(0, sender, ProofOfWork.of(6518542), recipient, System.currentTimeMillis());

            when(identity.getPublicKey()).thenReturn(recipient);

            final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, new HashMap<>(Map.of(sender, peer)), rendezvousPeers);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
            final TestObserver<Object> outboundMessages = pipeline.outboundOnlyMessages().test();

            pipeline.processInbound(address, discoveryMessage);

            outboundMessages.awaitCount(1).assertValueCount(1);
            outboundMessages.assertValue(m -> m instanceof IntermediateEnvelope && ((IntermediateEnvelope<?>) m).getPrivateHeader().getType() == ACKNOWLEDGEMENT);
            verify(peersManager, never()).setPeerInformationAndAddPath(any(), any(), any());
            pipeline.close();
        }

        @Test
        void shouldUpdatePeerInformationOnAcknowledgementMessageFromNormalPeer(@Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddressWrapper address,
                                                                               @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) throws IOException, CryptoException {
            final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
            final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
            final IntermediateEnvelope<Acknowledgement> acknowledgementMessage = IntermediateEnvelope.acknowledgement(0, sender, ProofOfWork.of(6518542), recipient, MessageId.randomMessageId());

            when(identity.getPublicKey()).thenReturn(recipient);

            final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(new HashMap<>(Map.of(MessageId.of(acknowledgementMessage.getBody().getCorrespondingId().toByteArray()), new OpenPing(address, false))), uniteAttemptsCache, new HashMap<>(Map.of(sender, peer)), rendezvousPeers);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

            pipeline.processInbound(address, acknowledgementMessage).join();

            verify(peersManager).setPeerInformationAndAddPath(any(), any(), any());
            pipeline.close();
        }

        @Test
        void shouldUpdatePeerInformationOnAcknowledgementMessageFromSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddressWrapper address,
                                                                              @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) throws IOException, CryptoException {
            final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
            final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
            final IntermediateEnvelope<Acknowledgement> acknowledgementMessage = IntermediateEnvelope.acknowledgement(0, sender, ProofOfWork.of(6518542), recipient, MessageId.randomMessageId());

            when(identity.getPublicKey()).thenReturn(recipient);

            final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(new HashMap<>(Map.of(MessageId.of(acknowledgementMessage.getBody().getCorrespondingId().toByteArray()), new OpenPing(address, true))), uniteAttemptsCache, new HashMap<>(Map.of(sender, peer)), rendezvousPeers);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

            pipeline.processInbound(address, acknowledgementMessage).join();

            verify(peersManager).setPeerInformationAndAddPathAndSetSuperPeer(any(), any(), any());
            pipeline.close();
        }

        @Test
        void shouldNotRemoveLivingSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                            @Mock final CompressedPublicKey publicKey,
                                            @Mock final InetSocketAddressWrapper address,
                                            @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            when(peer.getAddress()).thenReturn(address);

            final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, new HashMap<>(Map.of(publicKey, peer)), new HashSet<>());
            handler.doHeartbeat(ctx);

            verifyNoInteractions(peersManager);
        }

        @Test
        void shouldRemoveDeadSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                       @Mock final CompressedPublicKey publicKey,
                                       @Mock final InetSocketAddressWrapper address,
                                       @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            when(peer.getAddress()).thenReturn(address);
            when(ctx.config().getRemoteSuperPeerEndpoint().getPublicKey()).thenReturn(publicKey);

            final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, new HashMap<>(Map.of(publicKey, peer)), new HashSet<>());
            handler.doHeartbeat(ctx);

            verify(ctx.peersManager()).unsetSuperPeerAndRemovePath(any());
        }

        @Test
        void shouldRemoveDeadChildrenOrPeers(@Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                             @Mock final CompressedPublicKey publicKey,
                                             @Mock final InetSocketAddressWrapper address,
                                             @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) {
            when(peer.getAddress()).thenReturn(address);

            final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, new HashMap<>(Map.of(publicKey, peer)), new HashSet<>());
            handler.doHeartbeat(ctx);

            verify(ctx.peersManager()).removeChildrenAndPath(eq(publicKey), any());
        }

        @Test
        void shouldPingSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx) throws CryptoException {
            final CompressedPublicKey myPublicKey = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
            final CompressedPublicKey publicKey = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");

            when(ctx.config().isRemoteSuperPeerEnabled()).thenReturn(true);
            when(ctx.config().getRemoteSuperPeerEndpoint().getHost()).thenReturn("127.0.0.1");
            when(ctx.identity().getPublicKey()).thenReturn(myPublicKey);
            when(ctx.config().getRemoteSuperPeerEndpoint().getPublicKey()).thenReturn(publicKey);
            when(ctx.write(any(), any(ReferenceCounted.class), any())).then(invocation -> {
                ReferenceCountUtil.safeRelease(invocation.getArgument(1, ReferenceCounted.class));
                return null;
            });

            final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, peers, new HashSet<>());
            handler.doHeartbeat(ctx);

            verify(ctx).write(any(), any(IntermediateEnvelope.class), any());
        }

        @Test
        void shouldPingPeersWithRecentCommunication(@Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                                    @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) throws CryptoException {
            final CompressedPublicKey myPublicKey = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
            final CompressedPublicKey publicKey = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");

            when(peer.hasControlTraffic(any())).thenReturn(true);
            when(peer.hasApplicationTraffic(any())).thenReturn(true);
            when(ctx.identity().getPublicKey()).thenReturn(myPublicKey);
            when(ctx.write(any(), any(ReferenceCounted.class), any())).then(invocation -> {
                ReferenceCountUtil.safeRelease(invocation.getArgument(1, ReferenceCounted.class));
                return null;
            });

            final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, new HashMap<>(Map.of(publicKey, peer)), new HashSet<>(Set.of(publicKey)));
            handler.doHeartbeat(ctx);

            verify(ctx).write(any(), any(IntermediateEnvelope.class), any());
        }

        @Test
        void shouldNotPingPeersWithoutRecentCommunication(@Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                                          @Mock(answer = RETURNS_DEEP_STUBS) final Peer peer) throws CryptoException {
            final CompressedPublicKey publicKey = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");

            when(peer.hasControlTraffic(any())).thenReturn(true);

            final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, new HashMap<>(Map.of(publicKey, peer)), new HashSet<>(Set.of(publicKey)));
            handler.doHeartbeat(ctx);

            verify(ctx, never()).write(any(), any(), any());
            verify(ctx.peersManager()).removeChildrenAndPath(eq(publicKey), any());
        }
    }

    @Nested
    class Uniting {
        @Test
        void shouldHandleUniteMessageFromSuperPeer(@Mock(answer = RETURNS_DEEP_STUBS) final Peer peer,
                                                   @Mock(answer = RETURNS_DEEP_STUBS) final InetSocketAddressWrapper address) throws IOException, CryptoException {
            final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
            final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
            final IntermediateEnvelope<Unite> uniteMessage = IntermediateEnvelope.unite(0, sender, ProofOfWork.of(6518542), recipient, CompressedPublicKey.of("03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a"), new InetSocketAddress(22527));

            when(config.getRemoteSuperPeerEndpoint().getPublicKey()).thenReturn(sender);
            when(identity.getPublicKey()).thenReturn(recipient);

            final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, new HashMap<>(Map.of(CompressedPublicKey.of(uniteMessage.getBody().getPublicKey().toByteArray()), peer)), rendezvousPeers);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

            pipeline.processInbound(address, uniteMessage).join();

            verify(rendezvousPeers).add(any());

            ReferenceCountUtil.safeRelease(uniteMessage);
            pipeline.close();
        }

        @Test
        void shouldInitiateUniteForInboundMessageWithKnownSenderAndRecipient(@Mock final InetSocketAddressWrapper sender,
                                                                             @Mock(answer = RETURNS_DEEP_STUBS) final IntermediateEnvelope<MessageLite> message,
                                                                             @Mock(answer = RETURNS_DEEP_STUBS) final Peer senderPeer,
                                                                             @Mock(answer = RETURNS_DEEP_STUBS) final Peer recipientPeer) throws CryptoException {
            final InetSocketAddressWrapper senderSocketAddress = InetSocketAddressWrapper.of(new InetSocketAddress(80));
            final InetSocketAddressWrapper recipientSocketAddress = InetSocketAddressWrapper.of(new InetSocketAddress(81));
            final CompressedPublicKey myKey = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
            final CompressedPublicKey senderKey = CompressedPublicKey.of("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458");
            final CompressedPublicKey recipientKey = CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9");

            when(recipientPeer.isReachable(any())).thenReturn(true);
            when(senderPeer.getAddress()).thenReturn(senderSocketAddress);
            when(recipientPeer.getAddress()).thenReturn(recipientSocketAddress);
            when(identity.getPublicKey()).thenReturn(myKey);
            when(message.getSender()).thenReturn(senderKey);
            when(message.getRecipient()).thenReturn(recipientKey);

            final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, Map.of(message.getSender(), senderPeer, message.getRecipient(), recipientPeer), rendezvousPeers);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
            final TestObserver<Pair<Address, Object>> outboundMessages = pipeline.outboundMessages().test();

            pipeline.processInbound(sender, message).join();

            outboundMessages.awaitCount(3).assertValueCount(3);
            outboundMessages.assertValueAt(1, p -> p.first().equals(senderSocketAddress) && p.second() instanceof IntermediateEnvelope && ((IntermediateEnvelope<?>) p.second()).getPrivateHeader().getType() == UNITE);
            outboundMessages.assertValueAt(2, p -> p.first().equals(recipientSocketAddress) && p.second() instanceof IntermediateEnvelope && ((IntermediateEnvelope<?>) p.second()).getPrivateHeader().getType() == UNITE);
            pipeline.close();
        }
    }

    @Nested
    class ApplicationTrafficRouting {
        @Nested
        class Inbound {
            @Test
            void shouldRelayMessageForKnownRecipient(@Mock final InetSocketAddressWrapper sender,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final IntermediateEnvelope<MessageLite> message,
                                                     @Mock(answer = RETURNS_DEEP_STUBS) final Peer recipientPeer) {
                when(recipientPeer.isReachable(any())).thenReturn(true);

                final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, Map.of(message.getRecipient(), recipientPeer), rendezvousPeers);
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
                final TestObserver<Pair<Address, Object>> outboundMessages = pipeline.outboundMessages().test();

                pipeline.processInbound(sender, message).join();

                outboundMessages.awaitCount(1).assertValueCount(1);
                outboundMessages.assertValue(p -> p.first().equals(recipientPeer.getAddress()) && p.second().equals(message));
                pipeline.close();
            }

            @Test
            void shouldCompleteExceptionallyOnInvalidMessage(@Mock final InetSocketAddressWrapper sender,
                                                             @Mock(answer = RETURNS_DEEP_STUBS) final IntermediateEnvelope<MessageLite> message,
                                                             @Mock(answer = RETURNS_DEEP_STUBS) final Peer recipientPeer,
                                                             @Mock(answer = RETURNS_DEEP_STUBS) final CompressedPublicKey recipient) throws InterruptedException {
                when(message.getRecipient()).thenThrow(IllegalArgumentException.class);

                final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, Map.of(recipient, recipientPeer), rendezvousPeers);
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
                final TestObserver<Pair<Address, Object>> outboundMessages = pipeline.outboundMessages().test();

                assertThrows(ExecutionException.class, () -> pipeline.processInbound(sender, message).get());
                outboundMessages.await(1, SECONDS);
                outboundMessages.assertNoValues();
                pipeline.close();
            }

            @SuppressWarnings("SuspiciousMethodCalls")
            @Test
            void shouldUpdateLastCommunicationTimeAndConvertToApplicationMessageForRemoteApplicationMessages(
                    @Mock final Peer peer,
                    @Mock final InetSocketAddressWrapper address) throws CryptoException {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                final IntermediateEnvelope<Application> applicationMessage = IntermediateEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), new byte[]{});

                when(rendezvousPeers.contains(any())).thenReturn(true);
                when(identity.getPublicKey()).thenReturn(recipient);

                final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, new HashMap<>(Map.of(applicationMessage.getSender(), peer)), rendezvousPeers);
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
                final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();

                pipeline.processInbound(address, applicationMessage).join();

                verify(peer).applicationTrafficOccurred();
                inboundMessages.awaitCount(1).assertValueCount(1);
                inboundMessages.assertValue(p -> p.second() instanceof ApplicationMessage);

                ReferenceCountUtil.safeRelease(applicationMessage);
                pipeline.close();
            }
        }

        @Nested
        class Outbound {
            @Test
            void shouldRelayMessageToKnowRecipient(@Mock final InetSocketAddressWrapper recipientSocketAddress,
                                                   @Mock final Peer recipientPeer) throws CryptoException {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                final ApplicationMessage message = new ApplicationMessage(sender, recipient, byte[].class, "Hallo Welt".getBytes());

                when(recipientPeer.getAddress()).thenReturn(recipientSocketAddress);
                when(recipientPeer.isReachable(any())).thenReturn(true);
                when(identity.getPublicKey()).thenReturn(recipient);

                final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, Map.of(recipient, recipientPeer), rendezvousPeers);
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
                final TestObserver<Pair<Address, Object>> outboundMessages = pipeline.outboundMessages().test();

                pipeline.processOutbound(recipient, message).join();

                outboundMessages.awaitCount(1).assertValueCount(1);
                outboundMessages.assertValue(p -> p.first().equals(recipientSocketAddress) && p.second() instanceof IntermediateEnvelope && ((IntermediateEnvelope<?>) p.second()).getPrivateHeader().getType() == APPLICATION);
                pipeline.close();
            }

            @Test
            void shouldRelayMessageToSuperPeerForUnknownRecipient(@Mock final InetSocketAddressWrapper superPeerSocketAddress,
                                                                  @Mock(answer = RETURNS_DEEP_STUBS) final Peer superPeerPeer) throws CryptoException {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                final ApplicationMessage message = new ApplicationMessage(sender, recipient, byte[].class, "Hallo Welt".getBytes());

                when(superPeerPeer.getAddress()).thenReturn(superPeerSocketAddress);
                when(peersManager.getSuperPeerKey()).thenReturn(recipient);
                when(identity.getPublicKey()).thenReturn(recipient);

                final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, Map.of(recipient, superPeerPeer), rendezvousPeers);
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
                final TestObserver<Pair<Address, Object>> outboundMessages = pipeline.outboundMessages().test();

                pipeline.processOutbound(recipient, message).join();

                outboundMessages.awaitCount(1).assertValueCount(1);
                outboundMessages.assertValue(p -> p.first().equals(superPeerSocketAddress) && ((IntermediateEnvelope<?>) p.second()).getPrivateHeader().getType() == APPLICATION);
                pipeline.close();
            }

            @Test
            void shouldPassthroughForUnknownRecipientWhenNoSuperPeerIsPresent() throws CryptoException {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                final ApplicationMessage message = new ApplicationMessage(sender, recipient, byte[].class, "Hallo Welt".getBytes());

                when(identity.getPublicKey()).thenReturn(sender);

                final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, peers, rendezvousPeers);
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
                final TestObserver<Pair<Address, Object>> outboundMessages = pipeline.outboundMessages().test();

                pipeline.processOutbound(recipient, message).join();

                outboundMessages.awaitCount(1).assertValueCount(1);
                outboundMessages.assertValue(p -> p.first().equals(recipient) && ((IntermediateEnvelope<?>) p.second()).getPrivateHeader().getType() == APPLICATION);
                pipeline.close();
            }

            @SuppressWarnings("SuspiciousMethodCalls")
            @Test
            void shouldUpdateLastCommunicationTimeForApplicationMessages(@Mock final Peer peer) throws CryptoException {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                final ApplicationMessage message = new ApplicationMessage(sender, recipient, byte[].class, "Hallo Welt".getBytes());

                when(rendezvousPeers.contains(any())).thenReturn(true);
                when(identity.getPublicKey()).thenReturn(recipient);

                final UdpDiscoveryHandler handler = new UdpDiscoveryHandler(openPingsCache, uniteAttemptsCache, new HashMap<>(Map.of(message.getRecipient(), peer)), rendezvousPeers);
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

                pipeline.processOutbound(recipient, message).join();

                verify(peer).applicationTrafficOccurred();
                pipeline.close();
            }
        }
    }

    @Nested
    class TestPeer {
        @Mock
        private InetSocketAddressWrapper address;

        @Nested
        class Getter {
            @Test
            void shouldReturnCorrectValues() {
                final long lastInboundControlTrafficTime = System.currentTimeMillis() - 1000;
                final long lastInboundPongTime = System.currentTimeMillis() - 100;
                final long lastApplicationTrafficTime = System.currentTimeMillis();
                final Peer peer = new Peer(address, lastInboundControlTrafficTime, lastInboundPongTime, lastApplicationTrafficTime);

                assertEquals(address, peer.getAddress());
                assertEquals(lastInboundControlTrafficTime, peer.getLastInboundControlTrafficTime());
                assertEquals(lastApplicationTrafficTime, peer.getLastApplicationTrafficTime());
            }
        }

        @Nested
        class InboundControlTrafficOccurred {
            @Test
            void shouldUpdateTime() {
                final long lastInboundControlTrafficTime = System.currentTimeMillis() - 1000;
                final long lastInboundPongTime = System.currentTimeMillis();
                final long lastApplicationTrafficTime = System.currentTimeMillis();
                final Peer peer = new Peer(address, lastInboundControlTrafficTime, lastInboundPongTime, lastApplicationTrafficTime);

                peer.inboundControlTrafficOccurred();

                assertThat(peer.getLastInboundControlTrafficTime(), greaterThan(lastInboundControlTrafficTime));
            }
        }

        @Nested
        class InboundPongOccurred {
            @Test
            void shouldNotFail() {
                final long lastInboundControlTrafficTime = System.currentTimeMillis();
                final long lastInboundPongTime = System.currentTimeMillis() - 1000;
                final long lastApplicationTrafficTime = System.currentTimeMillis();
                final Peer peer = new Peer(address, lastInboundControlTrafficTime, lastInboundPongTime, lastApplicationTrafficTime);

                peer.inboundPongOccurred();

                assertTrue(true);
            }
        }

        @Nested
        class ApplicationTrafficOccurred {
            @Test
            void shouldUpdateTime() {
                final long lastInboundControlTrafficTime = System.currentTimeMillis();
                final long lastInboundPongTime = System.currentTimeMillis();
                final long lastApplicationTrafficTime = System.currentTimeMillis() - 1000;
                final Peer peer = new Peer(address, lastInboundControlTrafficTime, lastInboundPongTime, lastApplicationTrafficTime);

                peer.applicationTrafficOccurred();

                assertThat(peer.getLastApplicationTrafficTime(), greaterThan(lastApplicationTrafficTime));
            }
        }

        @Nested
        class HasApplicationTraffic {
            @Test
            void shouldReturnTrueIfTrafficIsPresent(@Mock final DrasylConfig config) {
                when(config.getRemotePingCommunicationTimeout()).thenReturn(ofSeconds(1));

                final Peer peer = new Peer(address, System.currentTimeMillis(), System.currentTimeMillis(), System.currentTimeMillis());

                assertTrue(peer.hasApplicationTraffic(config));
            }
        }

        @Nested
        class HasControlTraffic {
            @Test
            void shouldReturnTrueIfTrafficIsPresent(@Mock final DrasylConfig config) {
                when(config.getRemotePingTimeout()).thenReturn(ofSeconds(1));

                final Peer peer = new Peer(address, System.currentTimeMillis(), System.currentTimeMillis(), System.currentTimeMillis());

                assertTrue(peer.hasControlTraffic(config));
            }
        }

        @Nested
        class IsReachable {
            @Test
            void shouldReturnTrueIfPeerIsReachable(@Mock final DrasylConfig config) {
                when(config.getRemotePingTimeout()).thenReturn(ofSeconds(1));

                final Peer peer = new Peer(address, System.currentTimeMillis(), System.currentTimeMillis(), System.currentTimeMillis());
                assertTrue(peer.isReachable(config));
            }
        }
    }

    @Nested
    class TestOpenPing {
        @Mock
        private InetSocketAddressWrapper address;

        @Nested
        class Getter {
            @Test
            void shouldReturnCorrectValues() {
                final OpenPing ping = new OpenPing(address, true);

                assertEquals(address, ping.getAddress());
                assertTrue(ping.isChildrenJoin());
            }
        }

        @Nested
        class Equals {
            @Test
            void shouldRecognizeEqualPairs() {
                final OpenPing pingA = new OpenPing(address, true);
                final OpenPing pingB = new OpenPing(address, true);
                final OpenPing pingC = new OpenPing(address, false);

                assertEquals(pingA, pingA);
                assertEquals(pingA, pingB);
                assertEquals(pingB, pingA);
                assertNotEquals(null, pingA);
                assertNotEquals(pingA, pingC);
                assertNotEquals(pingC, pingA);
            }
        }

        @Nested
        class HashCode {
            @Test
            void shouldRecognizeEqualPairs() {
                final OpenPing pingA = new OpenPing(address, true);
                final OpenPing pingB = new OpenPing(address, true);
                final OpenPing pingC = new OpenPing(address, false);

                assertEquals(pingA.hashCode(), pingB.hashCode());
                assertNotEquals(pingA.hashCode(), pingC.hashCode());
                assertNotEquals(pingB.hashCode(), pingC.hashCode());
            }
        }

        @Test
        void toStringShouldReturnString() {
            final OpenPing ping = new OpenPing(address, true);

            assertNotNull(ping.toString());
        }
    }
}
