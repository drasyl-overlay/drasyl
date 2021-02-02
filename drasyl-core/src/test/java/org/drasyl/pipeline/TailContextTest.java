/*
 * Copyright (c) 2021.
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
package org.drasyl.pipeline;

import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.util.scheduler.DrasylScheduler;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TailContextTest {
    @Mock
    private Consumer<Event> eventConsumer;
    @Mock
    private HandlerContext ctx;
    @Mock
    private DrasylConfig config;
    @Mock
    private Pipeline pipeline;
    @Mock
    private DrasylScheduler dependentScheduler;
    @Mock
    private DrasylScheduler independentScheduler;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock
    private Serialization inboundSerialization;
    @Mock
    private Serialization outboundSerialization;
    @Mock
    private CompletableFuture<Void> future;

    @Nested
    class InGeneral {
        @Test
        void shouldReturnSelfAsHandler() {
            final TailContext tailContext = new TailContext(eventConsumer, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);

            assertEquals(tailContext, tailContext.handler());
        }

        @Test
        void shouldDoNothingOnHandlerAdded() {
            final TailContext tailContext = new TailContext(eventConsumer, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);

            tailContext.handlerAdded(ctx);

            verifyNoInteractions(ctx);
        }

        @Test
        void shouldDoNothingOnHandlerRemoved() {
            final TailContext tailContext = new TailContext(eventConsumer, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);

            tailContext.handlerRemoved(ctx);

            verifyNoInteractions(ctx);
        }
    }

    @Nested
    class OnWrite {
        @Test
        void shouldPassthroughsOnWrite() {
            final TailContext tailContext = new TailContext(eventConsumer, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
            final CompressedPublicKey recipient = mock(CompressedPublicKey.class);
            final Object msg = mock(Object.class);

            tailContext.write(ctx, recipient, msg, future);

            verify(ctx).write(eq(recipient), eq(msg), eq(future));
        }
    }

    @Nested
    class OnException {
        @Test
        void shouldThrowException() {
            final TailContext tailContext = new TailContext(eventConsumer, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
            final Exception exception = mock(Exception.class);

            assertThrows(Exception.class, () -> tailContext.exceptionCaught(ctx, exception));
            verifyNoInteractions(ctx);
        }
    }

    @Nested
    class OnEvent {
        @Test
        void shouldPassEventToConsumer() {
            final TailContext tailContext = new TailContext(eventConsumer, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
            final Event event = mock(Event.class);

            tailContext.eventTriggered(ctx, event, future);

            verify(eventConsumer).accept(eq(event));
            verifyNoInteractions(ctx);
        }

        @Test
        void shouldNotWriteToConsumerWhenFutureIsDone() {
            final TailContext tailContext = new TailContext(eventConsumer, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
            final Event event = mock(Event.class);

            when(future.isDone()).thenReturn(true);

            tailContext.eventTriggered(ctx, event, future);

            verifyNoInteractions(eventConsumer);
            verify(future, never()).complete(null);
        }
    }

    @Nested
    class OnRead {
        @Test
        void shouldPassMessageToApplication() {
            final TailContext tailContext = new TailContext(eventConsumer, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
            final CompressedPublicKey sender = mock(CompressedPublicKey.class);
            final Object msg = mock(Object.class);

            tailContext.read(ctx, sender, msg, future);

            verify(eventConsumer).accept(eq(new MessageEvent(sender, msg)));
            verifyNoInteractions(ctx);
        }

        @Test
        void shouldNotWriteToConsumerWhenFutureIsDone() {
            final TailContext tailContext = new TailContext(eventConsumer, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
            final CompressedPublicKey sender = mock(CompressedPublicKey.class);
            final Object msg = mock(Object.class);

            when(future.isDone()).thenReturn(true);

            tailContext.read(ctx, sender, msg, future);

            verifyNoInteractions(eventConsumer);
            verify(future, never()).complete(null);
        }

        @Test
        void shouldCompleteFutureAndNothingElseOnAutoSwallow() {
            final TailContext tailContext = new TailContext(eventConsumer, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
            final CompressedPublicKey recipient = mock(CompressedPublicKey.class);
            final AutoSwallow msg = new AutoSwallow() {
            };

            tailContext.read(ctx, recipient, msg, future);

            verify(future, never()).completeExceptionally(any());
            verify(future).complete(null);
        }
    }
}