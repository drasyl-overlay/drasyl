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
package org.drasyl.pipeline.handler;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.address.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintStream;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiPredicate;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessagesThroughputHandlerTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock
    BiPredicate<Address, Object> consumeOutbound;
    @Mock
    BiPredicate<Address, Object> consumeInbound;
    @Mock
    LongAdder outboundMessages;
    @Mock
    LongAdder inboundMessages;
    @Mock
    Scheduler scheduler;
    @Mock
    PrintStream printStream;
    @Mock
    Disposable disposable;

    @Test
    void shouldPrintThroughput(@Mock final NodeUpEvent nodeUp) {
        when(scheduler.schedulePeriodicallyDirect(any(), eq(0L), eq(1_000L), eq(MILLISECONDS))).then(invocation -> {
            final Runnable runnable = invocation.getArgument(0, Runnable.class);
            runnable.run();
            return null;
        });

        final Handler handler = new MessagesThroughputHandler(consumeOutbound, consumeInbound, outboundMessages, inboundMessages, scheduler, printStream, null);
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
            pipeline.processInbound(nodeUp).join();

            verify(printStream).printf(anyString(), any(), any(), any(), any());
        }
    }

    @Test
    void shouldStopTaskOnNodeUnrecoverableErrorEvent(@Mock final NodeUnrecoverableErrorEvent event) {
        final Handler handler = new MessagesThroughputHandler(consumeOutbound, consumeInbound, outboundMessages, inboundMessages, scheduler, printStream, disposable);
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
            pipeline.processInbound(event).join();

            verify(disposable).dispose();
        }
    }

    @Test
    void shouldStopTaskOnNodeDownEvent(@Mock final NodeDownEvent event) {
        final Handler handler = new MessagesThroughputHandler(consumeOutbound, consumeInbound, outboundMessages, inboundMessages, scheduler, printStream, disposable);
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
            pipeline.processInbound(event).join();

            verify(disposable).dispose();
        }
    }

    @Test
    void shouldRecordOutboundMessage(@Mock final Address address) {
        final Handler handler = new MessagesThroughputHandler(consumeOutbound, consumeInbound, outboundMessages, inboundMessages, scheduler, printStream, null);
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
            pipeline.processOutbound(address, new Object()).join();

            verify(outboundMessages).increment();
            verify(inboundMessages, never()).increment();
        }
    }

    @Test
    void shouldRecordInboundMessage(@Mock final Address address) {
        final Handler handler = new MessagesThroughputHandler(consumeOutbound, consumeInbound, outboundMessages, inboundMessages, scheduler, printStream, null);
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {

            pipeline.processInbound(address, new Object()).join();
        }

        verify(outboundMessages, never()).increment();
        verify(inboundMessages).increment();
    }

    @Test
    void shouldConsumeMatchingOutboundMessage(@Mock final Address address) {
        final Handler handler = new MessagesThroughputHandler((myAddress, msg) -> true, consumeInbound, outboundMessages, inboundMessages, scheduler, printStream, null);
        final TestObserver<Object> observable;
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
            observable = pipeline.outboundMessages().test();

            pipeline.processOutbound(address, new Object()).join();

            observable.assertEmpty();
        }
    }

    @Test
    void shouldConsumeMatchingInboundMessage(@Mock final Address address) {
        final Handler handler = new MessagesThroughputHandler(consumeOutbound, (myAddress, msg) -> true, outboundMessages, inboundMessages, scheduler, printStream, null);
        final TestObserver<Object> observable;
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
            observable = pipeline.inboundMessages().test();

            pipeline.processInbound(address, new Object()).join();

            observable.assertEmpty();
        }
    }
}
