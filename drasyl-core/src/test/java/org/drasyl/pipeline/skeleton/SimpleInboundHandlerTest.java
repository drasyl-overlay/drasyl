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
package org.drasyl.pipeline.skeleton;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.MessageId;
import org.drasyl.peer.connection.message.UserAgent;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.codec.ApplicationMessage2ObjectHolderHandler;
import org.drasyl.pipeline.codec.DefaultCodec;
import org.drasyl.pipeline.codec.ObjectHolder;
import org.drasyl.pipeline.codec.ObjectHolder2ApplicationMessageHandler;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.JSONUtil;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimpleInboundHandlerTest {
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    private DrasylConfig config;

    @BeforeEach
    void setUp() {
        config = DrasylConfig.newBuilder().build();
    }

    @Test
    void shouldTriggerOnMatchedMessage() throws JsonProcessingException {
        final SimpleInboundEventAwareHandler<byte[], Event, CompressedPublicKey> handler = new SimpleInboundEventAwareHandler<>() {
            @Override
            protected void matchedEventTriggered(final HandlerContext ctx,
                                                 final Event event,
                                                 final CompletableFuture<Void> future) {
                super.eventTriggered(ctx, event, future);
            }

            @Override
            protected void matchedRead(final HandlerContext ctx,
                                       final CompressedPublicKey sender,
                                       final byte[] msg,
                                       final CompletableFuture<Void> future) {
                // Emit this message as outbound message to test
                ctx.pipeline().processOutbound(sender, msg);
            }
        };

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                config,
                identity,
                peersManager,
                TypeValidator.ofInboundValidator(config),
                TypeValidator.ofOutboundValidator(config),
                ApplicationMessage2ObjectHolderHandler.INSTANCE,
                ObjectHolder2ApplicationMessageHandler.INSTANCE,
                DefaultCodec.INSTANCE, handler);
        final TestObserver<Pair<Address, Object>> inboundMessageTestObserver = pipeline.inboundMessages().test();
        final TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundOnlyMessages(ApplicationMessage.class).test();
        final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

        final CompressedPublicKey sender = mock(CompressedPublicKey.class);
        when(identity.getPublicKey()).thenReturn(sender);
        final ProofOfWork proofOfWork = mock(ProofOfWork.class);
        when(identity.getProofOfWork()).thenReturn(proofOfWork);
        final byte[] msg = JSONUtil.JACKSON_WRITER.writeValueAsBytes(new byte[]{});
        final int networkId = 1;
        pipeline.processInbound(new ApplicationMessage(networkId, sender, proofOfWork, sender, msg));

        outboundMessageTestObserver.awaitCount(1).assertValueCount(1);
        outboundMessageTestObserver.assertValue(new ApplicationMessage(networkId, sender, proofOfWork, sender, Map.of(ObjectHolder.CLASS_KEY_NAME, msg.getClass().getName()), msg));
        inboundMessageTestObserver.assertNoValues();
        eventTestObserver.assertNoValues();
    }

    @Test
    void shouldPassthroughsNotMatchingMessage() {
        final SimpleInboundEventAwareHandler<List<?>, Event, CompressedPublicKey> handler = new SimpleInboundEventAwareHandler<>() {
            @Override
            protected void matchedEventTriggered(final HandlerContext ctx,
                                                 final Event event,
                                                 final CompletableFuture<Void> future) {
                ctx.fireEventTriggered(event, future);
            }

            @Override
            protected void matchedRead(final HandlerContext ctx,
                                       final CompressedPublicKey sender,
                                       final List<?> msg,
                                       final CompletableFuture<Void> future) {
                // Emit this message as outbound message to test
                ctx.pipeline().processOutbound(sender, msg);
            }
        };

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                config,
                identity,
                peersManager,
                TypeValidator.ofInboundValidator(config),
                TypeValidator.ofOutboundValidator(config),
                ApplicationMessage2ObjectHolderHandler.INSTANCE,
                ObjectHolder2ApplicationMessageHandler.INSTANCE,
                DefaultCodec.INSTANCE, handler);
        final TestObserver<Pair<Address, Object>> inboundMessageTestObserver = pipeline.inboundMessages().test();
        final TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundOnlyMessages(ApplicationMessage.class).test();
        final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

        final byte[] payload = new byte[]{ 0x01 };
        final ApplicationMessage msg = mock(ApplicationMessage.class);

        when(msg.getSender()).thenReturn(mock(CompressedPublicKey.class));
        when(msg.getPayload()).thenReturn(payload);
        doReturn(payload.getClass().getName()).when(msg).getHeader(ObjectHolder.CLASS_KEY_NAME);

        pipeline.processInbound(msg);

        inboundMessageTestObserver.awaitCount(1).assertValueCount(1);
        inboundMessageTestObserver.assertValue(Pair.of(msg.getSender(), payload));
        eventTestObserver.awaitCount(1).assertValueCount(1);
        eventTestObserver.assertValue(new MessageEvent(msg.getSender(), payload));
        outboundMessageTestObserver.assertNoValues();
    }

    @Test
    void shouldTriggerOnMatchedEvent() throws InterruptedException {
        final SimpleInboundEventAwareHandler<ApplicationMessage, NodeUpEvent, CompressedPublicKey> handler = new SimpleInboundEventAwareHandler<>(ApplicationMessage.class, NodeUpEvent.class, CompressedPublicKey.class) {
            @Override
            protected void matchedEventTriggered(final HandlerContext ctx,
                                                 final NodeUpEvent event,
                                                 final CompletableFuture<Void> future) {
                // Do nothing
            }

            @Override
            protected void matchedRead(final HandlerContext ctx,
                                       final CompressedPublicKey sender,
                                       final ApplicationMessage msg,
                                       final CompletableFuture<Void> future) {
                ctx.fireRead(sender, msg, future);
            }
        };

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, mock(TypeValidator.class), mock(TypeValidator.class), handler);
        final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

        final NodeUpEvent event = mock(NodeUpEvent.class);
        pipeline.processInbound(event);

        eventTestObserver.await(1, TimeUnit.SECONDS);
        eventTestObserver.assertNoValues();
    }

    @Test
    void shouldPassthroughsNotMatchingEvents() {
        final SimpleInboundEventAwareHandler<MyMessage, NodeUpEvent, CompressedPublicKey> handler = new SimpleInboundEventAwareHandler<>() {
            @Override
            protected void matchedEventTriggered(final HandlerContext ctx,
                                                 final NodeUpEvent event,
                                                 final CompletableFuture<Void> future) {
                // Do nothing
            }

            @Override
            protected void matchedRead(final HandlerContext ctx,
                                       final CompressedPublicKey sender,
                                       final MyMessage msg,
                                       final CompletableFuture<Void> future) {
                ctx.fireRead(sender, msg, future);
            }
        };

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, mock(TypeValidator.class), mock(TypeValidator.class), handler);
        final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

        final Event event = mock(Event.class);
        pipeline.processInbound(event);

        eventTestObserver.awaitCount(1).assertValueCount(1);
        eventTestObserver.assertValue(event);
    }

    static class MyMessage implements Message {
        @Override
        public MessageId getId() {
            return null;
        }

        @Override
        public UserAgent getUserAgent() {
            return null;
        }

        @Override
        public int getNetworkId() {
            return 0;
        }

        @Override
        public CompressedPublicKey getSender() {
            return null;
        }

        @Override
        public ProofOfWork getProofOfWork() {
            return null;
        }

        @Override
        public CompressedPublicKey getRecipient() {
            return null;
        }

        @Override
        public short getHopCount() {
            return 0;
        }

        @Override
        public void incrementHopCount() {

        }
    }
}