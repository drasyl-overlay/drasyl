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
import org.drasyl.pipeline.handler.AddressedEnvelopeHandler;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.ApplicationMessage;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;
import org.drasyl.pipeline.serialization.SerializedApplicationMessage;
import org.drasyl.util.JSONUtil;
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
        void shouldTriggerOnMatchedMessage(@Mock final CompressedPublicKey sender,
                                           @Mock final CompressedPublicKey recipient) {
            when(identity.getPublicKey()).thenReturn(sender);
            final byte[] payload = new byte[]{};

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
                    ctx.pipeline().processInbound(identity.getPublicKey(), new SerializedApplicationMessage(identity.getPublicKey(), recipient, byte[].class.getName(), msg));
                }
            };

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, AddressedEnvelopeHandler.INSTANCE, handler)) {
                final TestObserver<AddressedEnvelope<Address, Object>> inboundMessageTestObserver = pipeline.inboundMessagesWithRecipient().test();
                final TestObserver<SerializedApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages(SerializedApplicationMessage.class).test();
                pipeline.processOutbound(recipient, payload);

                inboundMessageTestObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new DefaultAddressedEnvelope<>(sender, null, payload));
                outboundMessageTestObserver.assertNoValues();
            }
        }

        @Test
        void shouldPassthroughsNotMatchingMessage(@Mock final CompressedPublicKey sender,
                                                  @Mock final CompressedPublicKey recipient) {
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

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, AddressedEnvelopeHandler.INSTANCE, handler)) {
                final TestObserver<Object> inboundMessageTestObserver = pipeline.inboundMessages().test();
                final TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages(ApplicationMessage.class).test();

                when(identity.getPublicKey()).thenReturn(sender);
                final byte[] payload = new byte[]{};
                pipeline.processOutbound(recipient, payload);

                outboundMessageTestObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new ApplicationMessage(sender, recipient, payload));
                inboundMessageTestObserver.assertNoValues();
            }
        }
    }

    @Nested
    class InboundTest {
        @Test
        void shouldTriggerOnMatchedMessage(@Mock final CompressedPublicKey sender) throws JsonProcessingException {
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

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, AddressedEnvelopeHandler.INSTANCE, handler)) {
                final TestObserver<Object> inboundMessageTestObserver = pipeline.inboundMessages().test();
                final TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages(ApplicationMessage.class).test();
                final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

                when(identity.getPublicKey()).thenReturn(sender);
                final byte[] msg = JSONUtil.JACKSON_WRITER.writeValueAsBytes(new byte[]{});
                final SerializedApplicationMessage msg1 = new SerializedApplicationMessage(sender, sender, byte[].class.getName(), msg);
                pipeline.processInbound(msg1.getSender(), msg1);

                outboundMessageTestObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new ApplicationMessage(sender, sender, msg));
                inboundMessageTestObserver.assertNoValues();
                eventTestObserver.assertNoValues();
            }
        }

        @Test
        void shouldPassthroughsNotMatchingMessage(@Mock final SerializedApplicationMessage msg,
                                                  @Mock final CompressedPublicKey sender) {
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

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<AddressedEnvelope<Address, Object>> inboundMessageTestObserver = pipeline.inboundMessagesWithRecipient().test();
                final TestObserver<SerializedApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages(SerializedApplicationMessage.class).test();
                final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

                when(msg.getSender()).thenReturn(sender);

                pipeline.processInbound(msg.getSender(), msg);

                inboundMessageTestObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new DefaultAddressedEnvelope<>(msg.getSender(), null, msg));
                eventTestObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(MessageEvent.of(msg.getSender(), msg));
                outboundMessageTestObserver.assertNoValues();
            }
        }

        @Test
        void shouldTriggerOnMatchedEvent(@Mock final NodeUpEvent event) throws InterruptedException {
            final SimpleDuplexEventAwareHandler<SerializedApplicationMessage, NodeUpEvent, Object, Address> handler = new SimpleDuplexEventAwareHandler<>(SerializedApplicationMessage.class, NodeUpEvent.class, Object.class, CompressedPublicKey.class) {
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
                                           final SerializedApplicationMessage msg,
                                           final CompletableFuture<Void> future) {
                    ctx.fireRead(sender, msg, future);
                }
            };

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

                pipeline.processInbound(event);

                eventTestObserver.await(1, TimeUnit.SECONDS);
                eventTestObserver.assertNoValues();
            }
        }

        @Test
        void shouldPassthroughsNotMatchingEvents(@Mock final Event event) {
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

            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

                pipeline.processInbound(event);

                eventTestObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(event);
            }
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

