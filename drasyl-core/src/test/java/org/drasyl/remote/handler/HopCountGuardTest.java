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
package org.drasyl.remote.handler;

import com.google.protobuf.MessageLite;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.remote.protocol.AddressedIntermediateEnvelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HopCountGuardTest {
    @Mock
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;

    @Test
    void shouldPassMessagesThatHaveNotReachedTheirHopCountLimitAndIncrementHopCount(@Mock final CompressedPublicKey address,
                                                                                    @Mock(answer = RETURNS_DEEP_STUBS) final AddressedIntermediateEnvelope<MessageLite> message) throws IOException {
        when(config.getRemoteMessageHopLimit()).thenReturn((byte) 2);
        when(message.getContent().getHopCount()).thenReturn((byte) 1);

        final HopCountGuard handler = HopCountGuard.INSTANCE;
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
            final TestObserver<Object> outboundMessages = pipeline.outboundMessages().test();

            pipeline.processOutbound(address, message);

            outboundMessages.awaitCount(1)
                    .assertValueCount(1)
                    .assertValue(m -> m instanceof AddressedIntermediateEnvelope);
            verify(message.getContent()).incrementHopCount();
        }
    }

    @Test
    void shouldDiscardMessagesThatHaveReachedTheirHopCountLimit(@Mock final CompressedPublicKey address,
                                                                @Mock(answer = RETURNS_DEEP_STUBS) final AddressedIntermediateEnvelope<MessageLite> message) throws InterruptedException, IOException {
        when(config.getRemoteMessageHopLimit()).thenReturn((byte) 1);
        when(message.getContent().getHopCount()).thenReturn((byte) 1);
        when(message.refCnt()).thenReturn(1);

        final HopCountGuard handler = HopCountGuard.INSTANCE;
        try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
            final TestObserver<Object> outboundMessages = pipeline.outboundMessages().test();

            pipeline.processOutbound(address, message);

            outboundMessages.await(1, SECONDS);
            outboundMessages.assertNoValues();
        }
    }
}
