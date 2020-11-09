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
package org.drasyl.pipeline;

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtherNetworkFilterTest {
    @Mock
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private TypeValidator inboundValidator;
    @Mock
    private TypeValidator outboundValidator;

    @Test
    void shouldPassAllEvents(@Mock final Event event) {
        final OtherNetworkFilter handler = OtherNetworkFilter.INSTANCE;
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, inboundValidator, outboundValidator, handler);
        final TestObserver<Event> inboundEvents = pipeline.inboundEvents().test();

        pipeline.processInbound(event);

        inboundEvents.awaitCount(1).assertValueCount(1);
        inboundEvents.assertValue(event);
    }

    @Test
    void shouldDropMessagesFromOtherNetworks(@Mock(answer = RETURNS_DEEP_STUBS) final Message message) throws InterruptedException {
        when(config.getNetworkId()).thenReturn(123);
        when(message.getNetworkId()).thenReturn(456);

        final OtherNetworkFilter handler = OtherNetworkFilter.INSTANCE;
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, inboundValidator, outboundValidator, handler);
        final TestObserver<Pair<CompressedPublicKey, Object>> inboundMessages = pipeline.inboundMessages().test();

        pipeline.processInbound(message);

        inboundMessages.await(1, SECONDS);
        inboundMessages.assertNoValues();
    }

    @Test
    void shouldPassMessagesFromSameNetwork(@Mock(answer = RETURNS_DEEP_STUBS) final Message message) {
        when(config.getNetworkId()).thenReturn(123);
        when(message.getNetworkId()).thenReturn(123);

        final OtherNetworkFilter handler = OtherNetworkFilter.INSTANCE;
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, inboundValidator, outboundValidator, handler);
        final TestObserver<Pair<CompressedPublicKey, Object>> inboundMessages = pipeline.inboundMessages().test();

        pipeline.processInbound(message);

        inboundMessages.awaitCount(1).assertValueCount(1);
        inboundMessages.assertValue(Pair.of(message.getSender(), message));
    }
}