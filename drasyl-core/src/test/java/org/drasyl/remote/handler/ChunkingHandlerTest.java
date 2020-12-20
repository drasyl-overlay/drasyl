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

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ExecutionException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.drasyl.remote.handler.ChunkingHandler.MAX_MESSAGE_SIZE;
import static org.drasyl.remote.handler.ChunkingHandler.MTU;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChunkingHandlerTest {
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

    @Nested
    class OnIngoingMessage {
        @Nested
        class WhenAddressedToMe {
            @Test
            void shouldPassthroughNonChunkedMessage() {
                fail("not implemented");
            }

            @Test
            void shouldCacheChunkedMessageIfOtherChunksAreStillMissing() {
                fail("not implemented");
            }

            @Test
            void shouldBuildMessageAfterReceivingLastMissingChunk() {
                fail("not implemented");
            }
        }

        @Nested
        class WhenNotAddressedToMe {
            @Test
            void shouldPassthroughNonChunkedMessage() {
                fail("not implemented");
            }

            @Test
            void shouldPassthroughChunkedMessage() {
                fail("not implemented");
            }
        }
    }

    @Nested
    class OnOutgoingMessage {
        @Nested
        class FromMe {
            @Test
            @Timeout(value = 5_000, unit = MILLISECONDS)
            void shouldPassthroughMessageNotExceedingMtuSize(@Mock final Address address) throws CryptoException {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                when(identity.getPublicKey()).thenReturn(sender);

                final Object msg = IntermediateEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), new byte[MTU / 2]);
                final Handler handler = new ChunkingHandler();
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
                final TestObserver<Pair<Address, Object>> outboundMessages = pipeline.outboundMessages().test();

                pipeline.processOutbound(address, msg).join();

                outboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValueAt(0, p -> p.second().equals(msg));
            }

            @Test
            @Timeout(value = 5_000, unit = MILLISECONDS)
            void shouldDropMessageExceedingMaximumMessageSize(@Mock final Address address) throws CryptoException, InterruptedException {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                when(identity.getPublicKey()).thenReturn(sender);

                final Object msg = IntermediateEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), new byte[MAX_MESSAGE_SIZE]);
                final Handler handler = new ChunkingHandler();
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
                final TestObserver<Pair<Address, Object>> outboundMessages = pipeline.outboundMessages().test();

                assertThrows(ExecutionException.class, () -> pipeline.processOutbound(address, msg).get());
                outboundMessages.await(1, SECONDS);
                outboundMessages.assertNoValues();
            }

            @Test
            @Timeout(value = 5_000, unit = MILLISECONDS)
            void shouldChunkMessageExceedingMtuSize(@Mock final Address address) throws CryptoException {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                when(identity.getPublicKey()).thenReturn(sender);

                final Object msg = IntermediateEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), new byte[MTU * 2]);
                final Handler handler = new ChunkingHandler();
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
                final TestObserver<Pair<Address, Object>> outboundMessages = pipeline.outboundMessages().test();

                pipeline.processOutbound(address, msg).join();

                outboundMessages.awaitCount(3)
                        .assertValueCount(3)
                        .assertValueAt(0, p -> ((IntermediateEnvelope) p.second()).getChunkNo() == 0)
                        .assertValueAt(1, p -> ((IntermediateEnvelope) p.second()).getChunkNo() == 1)
                        .assertValueAt(2, p -> ((IntermediateEnvelope) p.second()).getChunkNo() == 2);
            }
        }

        @Nested
        class NotFromMe {
            @Test
            @Timeout(value = 5_000, unit = MILLISECONDS)
            void shouldPassthroughMessage(@Mock final Address address) throws CryptoException {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                when(identity.getPublicKey()).thenReturn(recipient);

                final Object msg = IntermediateEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), new byte[MTU / 2]);
                final Handler handler = new ChunkingHandler();
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
                final TestObserver<Pair<Address, Object>> outboundMessages = pipeline.outboundMessages().test();

                pipeline.processOutbound(address, msg).join();

                outboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValueAt(0, p -> p.second().equals(msg));
            }
        }
    }
}