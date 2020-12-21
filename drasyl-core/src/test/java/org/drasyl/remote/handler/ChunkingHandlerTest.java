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

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
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
import org.drasyl.remote.protocol.MessageId;
import org.drasyl.remote.protocol.Protocol;
import org.drasyl.remote.protocol.UserAgent;
import org.drasyl.util.Pair;
import org.drasyl.util.ReferenceCountUtil;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.drasyl.remote.protocol.MessageId.randomMessageId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private final int remoteMessageMtu = 1024;
    private final int remoteMaxContentLength = 10 * 1024;
    private final Duration messageComposedMessageTransferTimeout = ofSeconds(10);

    @Nested
    class OnIngoingMessage {
        @Nested
        class WhenAddressedToMe {
            @Test
            void shouldPassthroughNonChunkedMessage() throws CryptoException {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                when(identity.getPublicKey()).thenReturn(recipient);

                final Object msg = IntermediateEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), new byte[remoteMessageMtu / 2]);
                final Handler handler = new ChunkingHandler(remoteMessageMtu, remoteMaxContentLength, messageComposedMessageTransferTimeout);
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
                final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();

                pipeline.processInbound(sender, msg).join();

                inboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValueAt(0, p -> p.second().equals(msg));
            }

            @Test
            void shouldCacheChunkedMessageIfOtherChunksAreStillMissing() throws IOException, InterruptedException, CryptoException {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                final MessageId messageId = randomMessageId();
                final UserAgent userAgent = UserAgent.generate();
                final ProofOfWork proofOfWork = ProofOfWork.of(6518542);
                when(identity.getPublicKey()).thenReturn(recipient);

                final Handler handler = new ChunkingHandler(remoteMessageMtu, remoteMaxContentLength, messageComposedMessageTransferTimeout);
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
                final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();

                // head chunk
                final Protocol.PublicHeader headChunkHeader = Protocol.PublicHeader.newBuilder()
                        .setId(ByteString.copyFrom(messageId.byteArrayValue()))
                        .setUserAgent(ByteString.copyFrom(userAgent.getVersion().toBytes()))
                        .setSender(ByteString.copyFrom(sender.byteArrayValue())) // TODO: required?
                        .setProofOfWork(proofOfWork.intValue()) // TODO: required?
                        .setRecipient(ByteString.copyFrom(recipient.byteArrayValue()))
                        .setHopCount(ByteString.copyFrom(new byte[]{ (byte) 0 }))
                        .setTotalChunks(ByteString.copyFrom(new byte[]{ (byte) 2 }))
                        .build();
                final ByteBuf headChunkPayload = PooledByteBufAllocator.DEFAULT.buffer().writeBytes(new byte[remoteMessageMtu / 2]); // TODO: release byte buf?

                final IntermediateEnvelope<MessageLite> headChunk = IntermediateEnvelope.of(headChunkHeader, headChunkPayload);
                pipeline.processInbound(sender, headChunk).join();
                inboundMessages.await(1, SECONDS);
                inboundMessages.assertNoValues();
            }

            @Test
            void shouldBuildMessageAfterReceivingLastMissingChunk() throws CryptoException, IOException {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                final MessageId messageId = randomMessageId();
                final UserAgent userAgent = UserAgent.generate();
                final ProofOfWork proofOfWork = ProofOfWork.of(6518542);
                when(identity.getPublicKey()).thenReturn(recipient);

                final Handler handler = new ChunkingHandler(remoteMessageMtu, remoteMaxContentLength, messageComposedMessageTransferTimeout);
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
                final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();

                // normal chunk
                final Protocol.PublicHeader chunkHeader = Protocol.PublicHeader.newBuilder()
                        .setId(ByteString.copyFrom(messageId.byteArrayValue()))
                        .setUserAgent(ByteString.copyFrom(userAgent.getVersion().toBytes()))
                        .setSender(ByteString.copyFrom(sender.byteArrayValue())) // TODO: required?
                        .setProofOfWork(proofOfWork.intValue()) // TODO: required?
                        .setRecipient(ByteString.copyFrom(recipient.byteArrayValue()))
                        .setHopCount(ByteString.copyFrom(new byte[]{ (byte) 0 }))
                        .setChunkNo(ByteString.copyFrom(new byte[]{ (byte) 1 }))
                        .build();
                final ByteBuf chunkPayload = PooledByteBufAllocator.DEFAULT.buffer().writeBytes(new byte[remoteMessageMtu / 2]); // TODO: release byte buf?

                final IntermediateEnvelope<MessageLite> chunk = IntermediateEnvelope.of(chunkHeader, chunkPayload);
                pipeline.processInbound(sender, chunk).join();

                // head chunk
                final Protocol.PublicHeader headChunkHeader = Protocol.PublicHeader.newBuilder()
                        .setId(ByteString.copyFrom(messageId.byteArrayValue()))
                        .setUserAgent(ByteString.copyFrom(userAgent.getVersion().toBytes()))
                        .setSender(ByteString.copyFrom(sender.byteArrayValue())) // TODO: required?
                        .setProofOfWork(proofOfWork.intValue()) // TODO: required?
                        .setRecipient(ByteString.copyFrom(recipient.byteArrayValue()))
                        .setHopCount(ByteString.copyFrom(new byte[]{ (byte) 0 }))
                        .setTotalChunks(ByteString.copyFrom(new byte[]{ (byte) 2 }))
                        .build();
                final ByteBuf headChunkPayload = PooledByteBufAllocator.DEFAULT.buffer().writeBytes(new byte[remoteMessageMtu / 2]); // TODO: release byte buf?

                final IntermediateEnvelope<MessageLite> headChunk = IntermediateEnvelope.of(headChunkHeader, headChunkPayload);
                pipeline.processInbound(sender, headChunk).join();

                inboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValueAt(0, p -> {
                            final IntermediateEnvelope envelope = (IntermediateEnvelope) p.second();

                            try {
                                // TODO: Ein Test der auch guck, ob tatsächlich eine verschickte Nachricht wieder zusammengebaut wird,
                                // wäre gut.
//                                assertEquals(messageId, envelope.getId());
//                                assertEquals(userAgent, envelope.getUserAgent());
//                                assertEquals(sender, envelope.getSender());
//                                assertEquals(proofOfWork, envelope.getProofOfWork());
//                                assertEquals(recipient, envelope.getRecipient());

                                return !envelope.isChunk();
                            }
                            finally {
                                ReferenceCountUtil.safeRelease(envelope);
                            }
                        });
            }

            @Test
            void shouldCompleteExceptionallyWhenChunkedMessageExceedMaxSize() throws CryptoException, IOException, InterruptedException {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                final MessageId messageId = randomMessageId();
                final UserAgent userAgent = UserAgent.generate();
                final ProofOfWork proofOfWork = ProofOfWork.of(6518542);
                when(identity.getPublicKey()).thenReturn(recipient);

                final Handler handler = new ChunkingHandler(remoteMessageMtu, remoteMaxContentLength, messageComposedMessageTransferTimeout);
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
                final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();

                // head chunk
                final Protocol.PublicHeader headChunkHeader = Protocol.PublicHeader.newBuilder()
                        .setId(ByteString.copyFrom(messageId.byteArrayValue()))
                        .setUserAgent(ByteString.copyFrom(userAgent.getVersion().toBytes()))
                        .setSender(ByteString.copyFrom(sender.byteArrayValue())) // TODO: required?
                        .setProofOfWork(proofOfWork.intValue()) // TODO: required?
                        .setRecipient(ByteString.copyFrom(recipient.byteArrayValue()))
                        .setHopCount(ByteString.copyFrom(new byte[]{ (byte) 0 }))
                        .setTotalChunks(ByteString.copyFrom(new byte[]{ (byte) 2 }))
                        .build();
                final ByteBuf headChunkPayload = PooledByteBufAllocator.DEFAULT.buffer().writeBytes(new byte[remoteMaxContentLength]); // TODO: release byte buf?

                // normal chunk
                final Protocol.PublicHeader chunkHeader = Protocol.PublicHeader.newBuilder()
                        .setId(ByteString.copyFrom(messageId.byteArrayValue()))
                        .setUserAgent(ByteString.copyFrom(userAgent.getVersion().toBytes()))
                        .setSender(ByteString.copyFrom(sender.byteArrayValue())) // TODO: required?
                        .setProofOfWork(proofOfWork.intValue()) // TODO: required?
                        .setRecipient(ByteString.copyFrom(recipient.byteArrayValue()))
                        .setHopCount(ByteString.copyFrom(new byte[]{ (byte) 0 }))
                        .setChunkNo(ByteString.copyFrom(new byte[]{ (byte) 1 }))
                        .build();
                final ByteBuf chunkPayload = PooledByteBufAllocator.DEFAULT.buffer().writeBytes(new byte[remoteMaxContentLength]); // TODO: release byte buf?

                final IntermediateEnvelope<MessageLite> chunk = IntermediateEnvelope.of(chunkHeader, chunkPayload);
                pipeline.processInbound(sender, chunk).join();

                final IntermediateEnvelope<MessageLite> headChunk = IntermediateEnvelope.of(headChunkHeader, headChunkPayload);
                assertThrows(ExecutionException.class, () -> pipeline.processInbound(sender, headChunk).get());
                inboundMessages.await(1, SECONDS);
                inboundMessages.assertNoValues();
            }
        }

        @Nested
        class WhenNotAddressedToMe {
            @Test
            void shouldPassthroughNonChunkedMessage() throws CryptoException {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                when(identity.getPublicKey()).thenReturn(sender);

                final Object msg = IntermediateEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), new byte[remoteMessageMtu / 2]);
                final Handler handler = new ChunkingHandler(remoteMessageMtu, remoteMaxContentLength, messageComposedMessageTransferTimeout);
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
                final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();

                pipeline.processInbound(sender, msg).join();

                inboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValueAt(0, p -> p.second().equals(msg));
            }

            @Test
            void shouldPassthroughChunkedMessage() throws CryptoException, IOException {
                final CompressedPublicKey sender = CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
                final CompressedPublicKey recipient = CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4");
                final MessageId messageId = randomMessageId();
                final UserAgent userAgent = UserAgent.generate();
                final ProofOfWork proofOfWork = ProofOfWork.of(6518542);
                when(identity.getPublicKey()).thenReturn(sender);

                final Handler handler = new ChunkingHandler(remoteMessageMtu, remoteMaxContentLength, messageComposedMessageTransferTimeout);
                final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
                final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();

                final Protocol.PublicHeader headChunkHeader = Protocol.PublicHeader.newBuilder()
                        .setId(ByteString.copyFrom(messageId.byteArrayValue()))
                        .setUserAgent(ByteString.copyFrom(userAgent.getVersion().toBytes()))
                        .setSender(ByteString.copyFrom(sender.byteArrayValue())) // TODO: required?
                        .setProofOfWork(proofOfWork.intValue()) // TODO: required?
                        .setRecipient(ByteString.copyFrom(recipient.byteArrayValue()))
                        .setHopCount(ByteString.copyFrom(new byte[]{ (byte) 0 }))
                        .setTotalChunks(ByteString.copyFrom(new byte[]{ (byte) 2 }))
                        .build();
                final ByteBuf headChunkPayload = PooledByteBufAllocator.DEFAULT.buffer().writeBytes(new byte[remoteMessageMtu / 2]); // TODO: release byte buf?
                final IntermediateEnvelope<MessageLite> headChunk = IntermediateEnvelope.of(headChunkHeader, headChunkPayload);
                pipeline.processInbound(sender, headChunk).join();

                inboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValueAt(0, p -> ((IntermediateEnvelope) p.second()).isChunk());
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

                final Object msg = IntermediateEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), new byte[remoteMessageMtu / 2]);
                final Handler handler = new ChunkingHandler(remoteMessageMtu, remoteMaxContentLength, messageComposedMessageTransferTimeout);
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

                final Object msg = IntermediateEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), new byte[remoteMaxContentLength]);
                final Handler handler = new ChunkingHandler(remoteMessageMtu, remoteMaxContentLength, messageComposedMessageTransferTimeout);
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

                final Object msg = IntermediateEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), new byte[remoteMessageMtu * 2]);
                final Handler handler = new ChunkingHandler(remoteMessageMtu, remoteMaxContentLength, messageComposedMessageTransferTimeout);
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

                final Object msg = IntermediateEnvelope.application(0, sender, ProofOfWork.of(6518542), recipient, byte[].class.getName(), new byte[remoteMessageMtu / 2]);
                final Handler handler = new ChunkingHandler(remoteMessageMtu, remoteMaxContentLength, messageComposedMessageTransferTimeout);
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