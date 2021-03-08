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
package org.drasyl.remote.handler.portmapper;

import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.address.Address;
import org.drasyl.remote.protocol.AddressedByteBuf;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortMapperTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private PeersManager peersManager;

    @Nested
    class EventHandling {
        @Test
        void shouldStartFirstMethodOnNodeUpEvent(@Mock final PortMapping method,
                                                 @Mock final NodeUpEvent event) {
            final ArrayList<PortMapping> methods = new ArrayList<>(List.of(method));

            final PortMapper handler = new PortMapper(methods, 0, null);
            final TestObserver<Event> inboundEvents;
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                inboundEvents = pipeline.inboundEvents().test();

                pipeline.processInbound(event).join();

                inboundEvents.awaitCount(1)
                        .assertValueCount(1);
                verify(method).start(any(), any(), any());
            }
        }

        @Test
        void shouldStopCurrentMethodOnNodeDownEvent(@Mock final PortMapping method,
                                                    @Mock final NodeDownEvent event) {
            final ArrayList<PortMapping> methods = new ArrayList<>(List.of(method));

            final PortMapper handler = new PortMapper(methods, 0, null);
            final TestObserver<Event> inboundEvents;
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                inboundEvents = pipeline.inboundEvents().test();

                pipeline.processInbound(event).join();

                inboundEvents.awaitCount(1)
                        .assertValueCount(1);
                verify(method).stop(any());
            }
        }

        @Test
        void shouldStopCurrentMethodOnNodeUnrecoverableErrorEvent(@Mock final PortMapping method,
                                                                  @Mock final NodeUnrecoverableErrorEvent event,
                                                                  @Mock final Disposable retryTask) {
            final ArrayList<PortMapping> methods = new ArrayList<>(List.of(method));

            final PortMapper handler = new PortMapper(methods, 0, retryTask);
            final TestObserver<Event> inboundEvents;
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                inboundEvents = pipeline.inboundEvents().test();

                pipeline.processInbound(event).join();

                inboundEvents.awaitCount(1)
                        .assertValueCount(1);
                verify(method).stop(any());
                verify(retryTask).dispose();
            }
        }
    }

    @Nested
    class MessageHandling {
        @Test
        void shouldConsumeMethodMessages(@Mock final PortMapping method,
                                         @Mock final Address sender,
                                         @Mock final AddressedByteBuf msg) {
            when(method.acceptMessage(any())).thenReturn(true);

            final ArrayList<PortMapping> methods = new ArrayList<>(List.of(method));

            final PortMapper handler = new PortMapper(methods, 0, null);
            final TestObserver<Object> inboundMessages;
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                inboundMessages = pipeline.inboundMessages().test();

                pipeline.processInbound(sender, msg).join();

                inboundMessages.assertEmpty();
            }
        }

        @Test
        void shouldPassthroughNonMethodMessages(@Mock final PortMapping method,
                                                @Mock final Address sender,
                                                @Mock final AddressedByteBuf msg) {
            when(method.acceptMessage(any())).thenReturn(false);

            final ArrayList<PortMapping> methods = new ArrayList<>(List.of(method));

            final PortMapper handler = new PortMapper(methods, 0, null);
            final TestObserver<Object> inboundMessages;
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                inboundMessages = pipeline.inboundMessages().test();

                pipeline.processInbound(sender, msg).join();

                inboundMessages.awaitCount(1)
                        .assertValueCount(1);
            }
        }
    }

    @Nested
    class MethodCycling {
        @Test
        void shouldCycleToNextMethodOnFailure(@Mock final PortMapping method1,
                                              @Mock final PortMapping method2,
                                              @Mock final NodeUpEvent event) {
            doAnswer(invocation -> {
                final Runnable onFailure = invocation.getArgument(2, Runnable.class);
                onFailure.run();
                return null;
            }).when(method1).start(any(), any(), any());

            final ArrayList<PortMapping> methods = new ArrayList<>(List.of(method1, method2));

            final PortMapper handler = new PortMapper(methods, 0, null);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                pipeline.processInbound(event).join();

                verify(method2).start(any(), any(), any());
            }
        }
    }
}
