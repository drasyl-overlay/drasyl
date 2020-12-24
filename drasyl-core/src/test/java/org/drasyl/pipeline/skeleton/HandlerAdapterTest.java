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

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.pipeline.message.ApplicationMessage;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    private TypeValidator inboundValidator;
    @Mock
    private TypeValidator outboundValidator;
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
        void shouldPassthroughsOnWrite() {
            final HandlerAdapter handlerAdapter = new HandlerAdapter();

            final CompressedPublicKey recipient = mock(CompressedPublicKey.class);
            final Object msg = mock(Object.class);

            handlerAdapter.write(ctx, recipient, msg, future);

            verify(ctx).write(eq(recipient), eq(msg), eq(future));
        }
    }

    @Nested
    class Inbound {
        @Test
        void shouldPassthroughsOnRead() {
            final HandlerAdapter handlerAdapter = new HandlerAdapter();

            final CompressedPublicKey sender = mock(CompressedPublicKey.class);
            final Object msg = mock(Object.class);

            handlerAdapter.read(ctx, sender, msg, future);

            verify(ctx).fireRead(eq(sender), eq(msg), eq(future));
        }

        @Test
        void shouldPassthroughsOnEventTriggered() {
            final HandlerAdapter handlerAdapter = new HandlerAdapter();

            final Event event = mock(Event.class);

            handlerAdapter.eventTriggered(ctx, event, future);

            verify(ctx).fireEventTriggered(eq(event), eq(future));
        }

        @Test
        void shouldPassthroughsOnExceptionCaught() {
            final HandlerAdapter handlerAdapter = new HandlerAdapter();

            final Exception exception = mock(Exception.class);

            handlerAdapter.exceptionCaught(ctx, exception);

            verify(ctx).fireExceptionCaught(eq(exception));
        }

        @Test
        void shouldPassthroughsOnEventTriggeredWithMultipleHandler() {
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, IntStream.rangeClosed(1, 10).mapToObj(i -> new HandlerAdapter()).toArray(HandlerAdapter[]::new));
            final TestObserver<Event> events = pipeline.inboundEvents().test();

            final Event event = mock(Event.class);
            pipeline.processInbound(event);

            events.awaitCount(1).assertValueCount(1);
            events.assertValue(event);
            pipeline.close();
        }

        @Test
        void shouldPassthroughsOnReadWithMultipleHandler() {
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, IntStream.rangeClosed(1, 10).mapToObj(i -> new HandlerAdapter()).toArray(HandlerAdapter[]::new));
            final TestObserver<Pair<Address, Object>> events = pipeline.inboundMessages().test();

            final CompressedPublicKey sender = mock(CompressedPublicKey.class);
            final ApplicationMessage msg = mock(ApplicationMessage.class);
            when(msg.getSender()).thenReturn(sender);

            pipeline.processInbound(msg.getSender(), msg);

            events.awaitCount(1).assertValueCount(1);
            events.assertValue(Pair.of(sender, msg));
            pipeline.close();
        }
    }
}