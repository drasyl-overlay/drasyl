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
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.HandlerMask;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class SimpleInboundHandlerTest {
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock
    private DrasylConfig config;

    @Test
    void shouldTriggerOnMatchedMessage(@Mock final Address sender) {
        final SimpleInboundEventAwareHandler<byte[], Event, Address> handler = new SimpleInboundHandler<>() {
            @Override
            protected void matchedRead(final HandlerContext ctx,
                                       final Address sender,
                                       final byte[] msg,
                                       final CompletableFuture<Void> future) {
                ctx.fireRead(sender, new String(msg), future);
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

        pipeline.processInbound(sender, "Hallo Welt".getBytes());

        inboundMessageTestObserver.awaitCount(1).assertValueCount(1);
        inboundMessageTestObserver.assertValue(Pair.of(sender, "Hallo Welt"));
        pipeline.close();
    }

    @Test
    void shouldPassthroughsNotMatchingMessage(@Mock final Address sender) {
        final SimpleInboundHandler<byte[], Address> handler = new SimpleInboundHandler<>() {
            @Override
            protected void matchedRead(final HandlerContext ctx,
                                       final Address sender,
                                       final byte[] msg,
                                       final CompletableFuture<Void> future) {
                ctx.fireRead(sender, new String(msg), future);
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

        pipeline.processInbound(sender, 1337);

        inboundMessageTestObserver.assertNoValues();
        pipeline.close();
    }

    @Test
    void shouldReturnCorrectHandlerMask() {
        assertEquals(HandlerMask.READ_MASK, HandlerMask.mask(SimpleInboundHandler.class));
    }

    @Test
    void shouldReturnCorrectHandlerMaskForEventAwareHandler() {
        final int mask = HandlerMask.READ_MASK
                | HandlerMask.EVENT_TRIGGERED_MASK;

        assertEquals(mask, HandlerMask.mask(SimpleInboundEventAwareHandler.class));
    }
}