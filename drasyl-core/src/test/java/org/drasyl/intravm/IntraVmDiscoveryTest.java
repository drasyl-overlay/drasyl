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
package org.drasyl.intravm;

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReadWriteLock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntraVmDiscoveryTest {
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
    private final Map<Pair<Integer, CompressedPublicKey>, HandlerContext> discoveries = new HashMap<>();
    @Mock(answer = RETURNS_DEEP_STUBS)
    private ReadWriteLock lock;

    @Nested
    class StartDiscovery {
        @Test
        void shouldStartDiscoveryOnNodeUpEvent(@Mock final NodeUpEvent event) {
            final IntraVmDiscovery handler = new IntraVmDiscovery(discoveries, lock);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

            pipeline.processInbound(event).join();

            assertThat(discoveries, aMapWithSize(1));
        }
    }

    @Nested
    class StopDiscovery {
        @Test
        void shouldStopDiscoveryOnNodeUnrecoverableErrorEvent(@Mock final NodeUnrecoverableErrorEvent event,
                                                              @Mock final HandlerContext ctx) {
            discoveries.put(Pair.of(0, identity.getPublicKey()), ctx);
            final IntraVmDiscovery handler = new IntraVmDiscovery(discoveries, lock);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

            pipeline.processInbound(event).join();

            assertThat(discoveries, aMapWithSize(0));
        }

        @Test
        void shouldStopDiscoveryOnNodeDownEvent(@Mock final NodeDownEvent event,
                                                @Mock final HandlerContext ctx) {
            discoveries.put(Pair.of(0, identity.getPublicKey()), ctx);

            final IntraVmDiscovery handler = new IntraVmDiscovery(discoveries, lock);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

            pipeline.processInbound(event).join();

            assertThat(discoveries, aMapWithSize(0));
        }
    }

    @Nested
    class MessagePassing {
        @Test
        void shouldSendOutgoingMessageToKnownRecipient(@Mock final CompressedPublicKey recipient,
                                                       @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message,
                                                       @Mock final HandlerContext ctx) {
            discoveries.put(Pair.of(0, message.getRecipient()), ctx);
            when(ctx.fireRead(any(), any(), any())).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked") final CompletableFuture<Void> future = invocation.getArgument(2, CompletableFuture.class);
                future.complete(null);
                return null;
            });

            final IntraVmDiscovery handler = new IntraVmDiscovery(discoveries, lock);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

            pipeline.processOutbound(recipient, message).join();

            verify(ctx).fireRead(any(), any(), any());
            pipeline.close();
        }

        @Test
        void shouldPasstroughOutgoingMessageForUnknownRecipients(@Mock final CompressedPublicKey recipient,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message) {

            final IntraVmDiscovery handler = new IntraVmDiscovery(discoveries, lock);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
            final TestObserver<Pair<Address, Object>> outboundMessages = pipeline.outboundMessages().test();

            pipeline.processOutbound(recipient, message).join();

            outboundMessages.assertValueCount(1);
            pipeline.close();
        }

        @Test
        void shouldSendIngoingMessageToKnownRecipient(@Mock final CompressedPublicKey sender,
                                                      @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message,
                                                      @Mock final HandlerContext ctx) {
            discoveries.put(Pair.of(0, message.getRecipient()), ctx);
            when(ctx.fireRead(any(), any(), any())).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked") final CompletableFuture<Void> future = invocation.getArgument(2, CompletableFuture.class);
                future.complete(null);
                return null;
            });

            final IntraVmDiscovery handler = new IntraVmDiscovery(discoveries, lock);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

            pipeline.processInbound(sender, message).join();

            verify(ctx).fireRead(any(), any(), any());
            pipeline.close();
        }

        @Test
        void shouldPasstroughIngoingMessageForUnknownRecipients(@Mock final CompressedPublicKey sender,
                                                                @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message) {
            final IntraVmDiscovery handler = new IntraVmDiscovery(discoveries, lock);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
            final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();

            pipeline.processInbound(sender, message).join();

            inboundMessages.assertValueCount(1);
            pipeline.close();
        }
    }
}