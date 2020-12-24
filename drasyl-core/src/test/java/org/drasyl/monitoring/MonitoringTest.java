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
package org.drasyl.monitoring;

import com.google.protobuf.MessageLite;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitoringTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private TypeValidator inboundValidator;
    @Mock
    private TypeValidator outboundValidator;
    @Mock
    private PeersManager peersManager;
    private final Map<String, Counter> counters = new HashMap<>();
    @Mock
    private Function<HandlerContext, MeterRegistry> registrySupplier;
    @Mock
    private MeterRegistry registry;

    @Nested
    class StartMonitoring {
        @Test
        void shouldStartDiscoveryOnNodeUpEvent(@Mock final NodeUpEvent event,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final MeterRegistry registry) {
            when(registrySupplier.apply(any())).thenReturn(registry);

            final Monitoring handler = new Monitoring(counters, registrySupplier, null);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

            pipeline.processInbound(event).join();

            verify(registrySupplier).apply(any());
            pipeline.close();
        }
    }

    @Nested
    class StopDiscovery {
        @Test
        void shouldStopDiscoveryOnNodeUnrecoverableErrorEvent(@Mock final NodeUnrecoverableErrorEvent event) {
            final Monitoring handler = new Monitoring(counters, registrySupplier, registry);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

            pipeline.processInbound(event).join();

            verify(registry).close();
            pipeline.close();
        }

        @Test
        void shouldStopDiscoveryOnNodeDownEvent(@Mock final NodeDownEvent event) {
            final Monitoring handler = spy(new Monitoring(counters, registrySupplier, registry));
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

            pipeline.processInbound(event).join();

            verify(registry).close();
            pipeline.close();
        }
    }

    @Nested
    class MessagePassing {
        @Test
        void shouldPassthroughAllEvents(@Mock final Event event) {
            final Monitoring handler = spy(new Monitoring(counters, registrySupplier, registry));
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
            final TestObserver<Event> inboundEvents = pipeline.inboundEvents().test();

            pipeline.processInbound(event);

            inboundEvents.awaitCount(1).assertValue(event);
            pipeline.close();
        }

        @Test
        void shouldPassthroughInboundMessages(@Mock final Address sender,
                                              @Mock final IntermediateEnvelope<MessageLite> message) {
            final Monitoring handler = spy(new Monitoring(counters, registrySupplier, registry));
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
            final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();

            pipeline.processInbound(sender, message);

            inboundMessages.awaitCount(1).assertValueCount(1);
            pipeline.close();
        }

        @Test
        void shouldPassthroughOutboundMessages(@Mock final Address recipient,
                                               @Mock final IntermediateEnvelope<MessageLite> message) {
            final Monitoring handler = spy(new Monitoring(counters, registrySupplier, registry));
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
            final TestObserver<Pair<Address, Object>> outboundMessages = pipeline.outboundMessages().test();

            pipeline.processOutbound(recipient, message);

            outboundMessages.awaitCount(1).assertValueCount(1);
            pipeline.close();
        }
    }
}