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
package org.drasyl.remote.handler;

import com.google.protobuf.MessageLite;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvalidProofOfWorkFilterTest {
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock
    private TypeValidator inboundValidator;
    @Mock
    private TypeValidator outboundValidator;
    @Mock
    private DrasylConfig config;

    @Test
    void shouldDropMessagesWithInvalidProofOfWork(@Mock(answer = RETURNS_DEEP_STUBS) final IntermediateEnvelope<MessageLite> message) throws InterruptedException, IOException {
        when(message.getProofOfWork().isValid(any(), anyByte())).thenReturn(false);
        when(message.isChunk()).thenReturn(false);

        final InvalidProofOfWorkFilter handler = InvalidProofOfWorkFilter.INSTANCE;
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
        final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();

        pipeline.processInbound(message.getSender(), message);

        inboundMessages.await(1, SECONDS);
        inboundMessages.assertNoValues();
        pipeline.close();
    }

    @Test
    void shouldPassMessagesWithValidProofOfWork(@Mock(answer = RETURNS_DEEP_STUBS) final IntermediateEnvelope<MessageLite> message) throws IOException {
        when(message.getProofOfWork().isValid(any(), anyByte())).thenReturn(true);
        when(message.isChunk()).thenReturn(false);

        final InvalidProofOfWorkFilter handler = InvalidProofOfWorkFilter.INSTANCE;
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
        final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();

        pipeline.processInbound(message.getSender(), message);

        inboundMessages.awaitCount(1).assertValueCount(1);
        inboundMessages.assertValue(Pair.of(message.getSender(), message));
        pipeline.close();
    }

    @Test
    void shouldPassChunks(@Mock(answer = RETURNS_DEEP_STUBS) final IntermediateEnvelope<MessageLite> message) throws IOException {
        when(message.isChunk()).thenThrow(IOException.class);

        final InvalidProofOfWorkFilter handler = InvalidProofOfWorkFilter.INSTANCE;
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
        final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();

        final CompletableFuture<Void> future = pipeline.processInbound(message.getSender(), message);

        assertThrows(Exception.class, future::join);
        inboundMessages.assertNoValues();
        pipeline.close();
    }
}