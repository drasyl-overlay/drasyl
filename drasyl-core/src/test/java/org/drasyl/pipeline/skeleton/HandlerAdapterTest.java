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

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;
import org.drasyl.pipeline.serialization.SerializedApplicationMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandlerAdapterTest {
    @Mock
    private HandlerContext ctx;
    @Mock
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock
    private CompletableFuture<Void> future;

    @Test
    void shouldDoNothing() {
        final HandlerAdapter adapter = new HandlerAdapter() {
            @Override
            public void handlerAdded(final HandlerContext ctx) {
                super.handlerAdded(ctx);
            }

            @Override
            public void handlerRemoved(final HandlerContext ctx) {
                super.handlerRemoved(ctx);
            }
        };

        adapter.handlerAdded(ctx);
        adapter.handlerRemoved(ctx);

        verifyNoInteractions(ctx);
    }

    @Nested
    class Outbound {
        @Test
        void shouldPassthroughsOnWrite(@Mock final CompressedPublicKey recipient,
                                       @Mock final Object msg) {
            final HandlerAdapter handlerAdapter = new HandlerAdapter();

            handlerAdapter.write(ctx, recipient, msg, future);

            verify(ctx).write(recipient, msg, future);
        }
    }

    @Nested
    class Inbound {
        @Test
        void shouldPassthroughsOnRead(@Mock final CompressedPublicKey sender,
                                      @Mock final Object msg) {
            final HandlerAdapter handlerAdapter = new HandlerAdapter();

            handlerAdapter.read(ctx, sender, msg, future);

            verify(ctx).fireRead(sender, msg, future);
        }

        @Test
        void shouldPassthroughsOnEventTriggered(@Mock final Event event) {
            final HandlerAdapter handlerAdapter = new HandlerAdapter();

            handlerAdapter.eventTriggered(ctx, event, future);

            verify(ctx).fireEventTriggered(event, future);
        }

        @Test
        void shouldPassthroughsOnExceptionCaught(@Mock final Exception exception) {
            final HandlerAdapter handlerAdapter = new HandlerAdapter();

            handlerAdapter.exceptionCaught(ctx, exception);

            verify(ctx).fireExceptionCaught(exception);
        }

        @Test
        void shouldPassthroughsOnEventTriggeredWithMultipleHandler(@Mock final Event event) {
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, IntStream.rangeClosed(1, 10).mapToObj(i -> new HandlerAdapter()).toArray(HandlerAdapter[]::new))) {
                final TestObserver<Event> events = pipeline.inboundEvents().test();

                pipeline.processInbound(event);

                events.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(event);
            }
        }

        @Test
        void shouldPassthroughsOnReadWithMultipleHandler(@Mock final CompressedPublicKey sender,
                                                         @Mock final SerializedApplicationMessage msg) {
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, IntStream.rangeClosed(1, 10).mapToObj(i -> new HandlerAdapter()).toArray(HandlerAdapter[]::new))) {
                final TestObserver<AddressedEnvelope<Address, Object>> inboundMessages = pipeline.inboundMessagesWithRecipient().test();

                when(msg.getSender()).thenReturn(sender);

                pipeline.processInbound(msg.getSender(), msg);

                inboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new DefaultAddressedEnvelope<>(sender, null, msg));
            }
        }
    }
}
