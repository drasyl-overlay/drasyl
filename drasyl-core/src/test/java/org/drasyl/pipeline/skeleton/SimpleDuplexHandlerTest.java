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
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.HandlerMask;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.codec.DefaultCodec;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.ApplicationMessage;
import org.drasyl.util.JSONUtil;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimpleDuplexHandlerTest {
    private final int networkId = 1;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    private DrasylConfig config;

    @BeforeEach
    void setUp() {
        config = DrasylConfig.newBuilder()
                .networkId(1)
                .build();
    }

    @Nested
    class OutboundTest {
        @Test
        void shouldTriggerOnMatchedMessage() {
            final CompressedPublicKey sender = mock(CompressedPublicKey.class);
            when(identity.getPublicKey()).thenReturn(sender);
            final byte[] payload = new byte[]{};
            final CompressedPublicKey recipient = mock(CompressedPublicKey.class);

            final SimpleDuplexHandler<Object, byte[], CompressedPublicKey> handler = new SimpleDuplexHandler<>() {
                @Override
                protected void matchedEventTriggered(final HandlerContext ctx, final Event event,
                                                     final CompletableFuture<Void> future) {
                    ctx.fireEventTriggered(event, future);
                }

                @Override
                protected void matchedRead(final HandlerContext ctx,
                                           final CompressedPublicKey sender,
                                           final Object msg,
                                           final CompletableFuture<Void> future) {
                    ctx.fireRead(sender, msg, future);
                }

                @Override
                protected void matchedWrite(final HandlerContext ctx,
                                            final CompressedPublicKey recipient,
                                            final byte[] msg,
                                            final CompletableFuture<Void> future) {
                    // Emit this message as inbound message to test
                    ctx.pipeline().processInbound(identity.getPublicKey(), new ApplicationMessage(identity.getPublicKey(), recipient, byte[].class, msg));
                }
            };

            final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                    config,
                    identity,
                    peersManager,
                    TypeValidator.ofInboundValidator(config),
                    TypeValidator.ofOutboundValidator(config),
                    DefaultCodec.INSTANCE, handler);
            final TestObserver<Pair<Address, Object>> inboundMessageTestObserver = pipeline.inboundMessages().test();
            final TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundOnlyMessages(ApplicationMessage.class).test();
            pipeline.processOutbound(recipient, payload);

            inboundMessageTestObserver.awaitCount(1).assertValueCount(1);
            inboundMessageTestObserver.assertValue(Pair.of(sender, payload));
            outboundMessageTestObserver.assertNoValues();
            pipeline.close();
        }

        @Test
        void shouldPassthroughsNotMatchingMessage() {
            final SimpleDuplexEventAwareHandler<Object, Event, MyMessage, CompressedPublicKey> handler = new SimpleDuplexEventAwareHandler<>(Object.class, Event.class, MyMessage.class, CompressedPublicKey.class) {
                @Override
                protected void matchedEventTriggered(final HandlerContext ctx,
                                                     final Event event,
                                                     final CompletableFuture<Void> future) {
                    ctx.fireEventTriggered(event, future);
                }

                @Override
                protected void matchedRead(final HandlerContext ctx,
                                           final CompressedPublicKey sender,
                                           final Object msg,
                                           final CompletableFuture<Void> future) {
                    ctx.fireRead(sender, msg, future);
                }

                @Override
                protected void matchedWrite(final HandlerContext ctx,
                                            final CompressedPublicKey recipient,
                                            final MyMessage msg,
                                            final CompletableFuture<Void> future) {
                    // Emit this message as inbound message to test
                    ctx.pipeline().processInbound(msg.getSender(), msg);
                }
            };

            final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                    config,
                    identity,
                    peersManager,
                    TypeValidator.ofInboundValidator(config),
                    TypeValidator.ofOutboundValidator(config),
                    DefaultCodec.INSTANCE, handler);
            final TestObserver<Pair<Address, Object>> inboundMessageTestObserver = pipeline.inboundMessages().test();
            final TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundOnlyMessages(ApplicationMessage.class).test();

            final CompressedPublicKey sender = mock(CompressedPublicKey.class);
            when(identity.getPublicKey()).thenReturn(sender);
            final CompressedPublicKey recipient = mock(CompressedPublicKey.class);
            final byte[] payload = new byte[]{};
            pipeline.processOutbound(recipient, payload);

            outboundMessageTestObserver.awaitCount(1).assertValueCount(1);
            outboundMessageTestObserver.assertValue(new ApplicationMessage(sender, recipient, byte[].class, payload));
            inboundMessageTestObserver.assertNoValues();
            pipeline.close();
        }
    }

    @Nested
    class InboundTest {
        @Test
        void shouldTriggerOnMatchedMessage() throws JsonProcessingException {
            final SimpleDuplexEventAwareHandler<byte[], Event, Object, Address> handler = new SimpleDuplexEventAwareHandler<>() {
                @Override
                protected void matchedWrite(final HandlerContext ctx,
                                            final Address recipient,
                                            final Object msg,
                                            final CompletableFuture<Void> future) {
                    ctx.write(recipient, msg, future);
                }

                @Override
                protected void matchedEventTriggered(final HandlerContext ctx,
                                                     final Event event,
                                                     final CompletableFuture<Void> future) {
                    super.eventTriggered(ctx, event, future);
                }

                @Override
                protected void matchedRead(final HandlerContext ctx,
                                           final Address sender,
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
                    DefaultCodec.INSTANCE, handler);
            final TestObserver<Pair<Address, Object>> inboundMessageTestObserver = pipeline.inboundMessages().test();
            final TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundOnlyMessages(ApplicationMessage.class).test();
            final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

            final CompressedPublicKey sender = mock(CompressedPublicKey.class);
            when(identity.getPublicKey()).thenReturn(sender);
            final byte[] msg = JSONUtil.JACKSON_WRITER.writeValueAsBytes(new byte[]{});
            final ApplicationMessage msg1 = new ApplicationMessage(sender, sender, byte[].class, msg);
            pipeline.processInbound(msg1.getSender(), msg1);

            outboundMessageTestObserver.awaitCount(1).assertValueCount(1);
            outboundMessageTestObserver.assertValue(new ApplicationMessage(sender, sender, byte[].class, msg));
            inboundMessageTestObserver.assertNoValues();
            eventTestObserver.assertNoValues();
            pipeline.close();
        }

        @Test
        void shouldPassthroughsNotMatchingMessage() {
            final SimpleDuplexHandler<List<?>, Object, Address> handler = new SimpleDuplexHandler<>() {
                @Override
                protected void matchedWrite(final HandlerContext ctx,
                                            final Address recipient,
                                            final Object msg,
                                            final CompletableFuture<Void> future) {
                    ctx.write(recipient, msg, future);
                }

                @Override
                protected void matchedEventTriggered(final HandlerContext ctx,
                                                     final Event event,
                                                     final CompletableFuture<Void> future) {
                    ctx.fireEventTriggered(event, future);
                }

                @Override
                protected void matchedRead(final HandlerContext ctx,
                                           final Address sender,
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
                    handler);
            final TestObserver<Pair<Address, Object>> inboundMessageTestObserver = pipeline.inboundMessages().test();
            final TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundOnlyMessages(ApplicationMessage.class).test();
            final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

            final byte[] payload = new byte[]{ 0x01 };
            final ApplicationMessage msg = mock(ApplicationMessage.class);
            when(msg.getSender()).thenReturn(mock(CompressedPublicKey.class));

            pipeline.processInbound(msg.getSender(), msg);

            inboundMessageTestObserver.awaitCount(1).assertValueCount(1);
            inboundMessageTestObserver.assertValue(Pair.of(msg.getSender(), msg));
            eventTestObserver.awaitCount(1).assertValueCount(1);
            eventTestObserver.assertValue(new MessageEvent(msg.getSender(), msg));
            outboundMessageTestObserver.assertNoValues();
            pipeline.close();
        }

        @Test
        void shouldTriggerOnMatchedEvent() throws InterruptedException {
            final SimpleDuplexEventAwareHandler<ApplicationMessage, NodeUpEvent, Object, Address> handler = new SimpleDuplexEventAwareHandler<>(ApplicationMessage.class, NodeUpEvent.class, Object.class, CompressedPublicKey.class) {
                @Override
                protected void matchedWrite(final HandlerContext ctx,
                                            final Address recipient,
                                            final Object msg,
                                            final CompletableFuture<Void> future) {
                    ctx.write(recipient, msg, future);
                }

                @Override
                protected void matchedEventTriggered(final HandlerContext ctx,
                                                     final NodeUpEvent event,
                                                     final CompletableFuture<Void> future) {
                    // Do nothing
                }

                @Override
                protected void matchedRead(final HandlerContext ctx,
                                           final Address sender,
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
            pipeline.close();
        }

        @Test
        void shouldPassthroughsNotMatchingEvents() {
            final SimpleDuplexEventAwareHandler<MyMessage, NodeUpEvent, Object, Address> handler = new SimpleDuplexEventAwareHandler<>() {
                @Override
                protected void matchedWrite(final HandlerContext ctx,
                                            final Address recipient,
                                            final Object msg,
                                            final CompletableFuture<Void> future) {
                    ctx.write(recipient, msg, future);
                }

                @Override
                protected void matchedEventTriggered(final HandlerContext ctx,
                                                     final NodeUpEvent event,
                                                     final CompletableFuture<Void> future) {
                    // Do nothing
                }

                @Override
                protected void matchedRead(final HandlerContext ctx,
                                           final Address sender,
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
            pipeline.close();
        }

        @Test
        void shouldReturnCorrectHandlerMask() {
            final int mask = HandlerMask.ALL
                    & ~HandlerMask.EXCEPTION_CAUGHT_MASK
                    & ~HandlerMask.EVENT_TRIGGERED_MASK;

            assertEquals(mask, HandlerMask.mask(SimpleDuplexHandler.class));
        }

        @Test
        void shouldReturnCorrectHandlerMaskForEventAwareHandler() {
            final int mask = HandlerMask.ALL
                    & ~HandlerMask.EXCEPTION_CAUGHT_MASK;

            assertEquals(mask, HandlerMask.mask(SimpleDuplexEventAwareHandler.class));
        }
    }

    static class MyMessage implements AddressedEnvelope<CompressedPublicKey, Object> {
        @Override
        public CompressedPublicKey getSender() {
            return null;
        }

        @Override
        public CompressedPublicKey getRecipient() {
            return null;
        }

        @Override
        public Object getContent() {
            return null;
        }
    }
}

