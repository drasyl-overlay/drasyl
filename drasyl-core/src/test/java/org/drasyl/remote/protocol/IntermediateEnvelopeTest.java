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
package org.drasyl.remote.protocol;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import org.drasyl.crypto.CryptoException;
import org.drasyl.crypto.Signature;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.remote.protocol.Protocol.Acknowledgement;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.remote.protocol.Protocol.Discovery;
import org.drasyl.remote.protocol.Protocol.PrivateHeader;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.drasyl.remote.protocol.Protocol.Unite;
import org.drasyl.util.ReferenceCountUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.drasyl.remote.protocol.Protocol.MessageType.ACKNOWLEDGEMENT;
import static org.drasyl.remote.protocol.Protocol.MessageType.APPLICATION;
import static org.drasyl.remote.protocol.Protocol.MessageType.DISCOVERY;
import static org.drasyl.remote.protocol.Protocol.MessageType.UNITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntermediateEnvelopeTest {
    private CompressedPublicKey senderPublicKey;
    private CompressedPrivateKey senderPrivateKey;
    private CompressedPublicKey recipientPublicKey;
    private CompressedPrivateKey recipientPrivateKey;
    private ByteBuf message;
    private PublicHeader publicHeader;
    private PrivateHeader privateHeader;
    private Application body;
    private int publicHeaderLength;
    private int privateHeaderLength;
    private int bodyLength;
    private MessageId messageId;
    private ProofOfWork senderProofOfWork;

    @BeforeEach
    void setUp() throws IOException, CryptoException {
        senderPublicKey = CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9");
        senderPrivateKey = CompressedPrivateKey.of("0b01459ef93b2b7dc22794a3b9b7e8fac293399cf9add5b2375d9c357a64546d");
        recipientPublicKey = CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3");
        recipientPrivateKey = CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34");
        messageId = MessageId.of("412176952b5b81fd13f84a7c");
        senderProofOfWork = ProofOfWork.of(6657650);
        publicHeader = PublicHeader.newBuilder()
                .setId(ByteString.copyFrom(messageId.byteArrayValue()))
                .setUserAgent(ByteString.copyFrom(UserAgent.generate().getVersion().toBytes()))
                .setNetworkId(1)
                .setSender(ByteString.copyFrom(senderPublicKey.byteArrayValue()))
                .setProofOfWork(senderProofOfWork.intValue())
                .setRecipient(ByteString.copyFrom(recipientPublicKey.byteArrayValue()))
                .setHopCount(ByteString.copyFrom(new byte[]{ (byte) 0 }))
                .build();

        privateHeader = PrivateHeader.newBuilder()
                .setType(APPLICATION)
                .build();

        body = Application.newBuilder()
                .setPayload(ByteString.copyFrom("Lorem ipsum dolor sit amet".getBytes())).build();

        message = Unpooled.buffer();
        final ByteBufOutputStream outputStream = new ByteBufOutputStream(message);
        publicHeader.writeDelimitedTo(outputStream);
        publicHeaderLength = outputStream.writtenBytes();
        privateHeader.writeDelimitedTo(outputStream);
        privateHeaderLength = outputStream.writtenBytes() - publicHeaderLength;
        body.writeDelimitedTo(outputStream);
        bodyLength = outputStream.writtenBytes() - publicHeaderLength - privateHeaderLength;
        outputStream.close();
    }

    @Nested
    class Of {
        @Nested
        class WithByteBuf {
            @Test
            void shouldOnlyReturnHeaderAndNotChangingTheUnderlyingByteBuf() throws IOException {
                try {
                    final byte[] backedByte = message.array();
                    final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

                    assertEquals(publicHeader, envelope.getPublicHeader());
                    assertEquals((privateHeaderLength + bodyLength), envelope.getInternalByteBuf().readableBytes());
                    assertEquals(backedByte, envelope.getInternalByteBuf().array());
                    assertEquals((publicHeaderLength + privateHeaderLength + bodyLength), envelope.getByteBuf().readableBytes());
                    assertEquals((publicHeaderLength + privateHeaderLength + bodyLength), message.readableBytes());
                }
                finally {
                    ReferenceCountUtil.safeRelease(message);
                }
            }

            @Test
            void shouldOnlyReturnPrivateHeaderButReadAlsoPublicHeader() throws IOException {
                try {
                    final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

                    assertEquals(privateHeader, envelope.getPrivateHeader());
                    assertEquals(bodyLength, envelope.getInternalByteBuf().readableBytes());
                    assertEquals((publicHeaderLength + privateHeaderLength + bodyLength), envelope.getByteBuf().readableBytes());
                    assertEquals((publicHeaderLength + privateHeaderLength + bodyLength), message.readableBytes());
                }
                finally {
                    ReferenceCountUtil.safeRelease(message);
                }
            }

            @Test
            void shouldOnlyReturnBodyButReadAll() throws IOException {
                try {
                    final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

                    assertEquals(body, envelope.getBody());
                    assertEquals(0, envelope.getInternalByteBuf().readableBytes());
                    assertEquals((publicHeaderLength + privateHeaderLength + bodyLength), envelope.getByteBuf().readableBytes());
                    assertEquals((publicHeaderLength + privateHeaderLength + bodyLength), message.readableBytes());
                }
                finally {
                    ReferenceCountUtil.safeRelease(message);
                }
            }

            @Test
            void shouldBuildByteBufOnMissingByteBuf() throws IOException {
                try {
                    final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

                    assertNotNull(envelope.getByteBuf());
                    assertNotNull(envelope.getInternalByteBuf());
                    assertNotNull(envelope.getBodyAndRelease());

                    assertNull(envelope.getByteBuf());
                    assertNull(envelope.getInternalByteBuf());

                    assertNotNull(envelope.getOrBuildInternalByteBuf());
                    assertNotNull(envelope.getByteBuf());
                    assertNotNull(envelope.getInternalByteBuf());
                    assertNotNull(envelope.getBodyAndRelease());
                }
                finally {
                    ReferenceCountUtil.safeRelease(message);
                }
            }

            @Test
            void shouldShareRefCnt() {
                final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

                assertEquals(envelope.getByteBuf().refCnt(), envelope.getInternalByteBuf().refCnt());
                assertEquals(1, envelope.getInternalByteBuf().refCnt());
                assertEquals(1, envelope.getByteBuf().refCnt());
                envelope.release();
                assertEquals(envelope.getByteBuf().refCnt(), envelope.getInternalByteBuf().refCnt());
                assertEquals(0, envelope.getInternalByteBuf().refCnt());
                assertEquals(0, envelope.getByteBuf().refCnt());
            }

            @Test
            void shouldThrowExceptionForNonReadableByteBuf(@Mock final ByteBuf message) {
                assertThrows(IllegalArgumentException.class, () -> IntermediateEnvelope.of(message));
            }
        }
    }

    @Nested
    class ReferenceCounted {
        @Mock
        ByteBuf originalMessage;
        @Mock
        ByteBuf message;
        MessageLite body;
        IntermediateEnvelope<MessageLite> underTest;

        @BeforeEach
        void setUp() {
            underTest = new IntermediateEnvelope<>(originalMessage, message, publicHeader, privateHeader, body);
        }

        @Test
        void refCntShouldBeCalledOnOriginalMessage() {
            underTest.refCnt();

            verify(originalMessage).refCnt();
        }

        @Test
        void retainShouldBeCalledOnOriginalMessage() {
            underTest.retain();

            verify(originalMessage).retain();
        }

        @Test
        void retainWithIncrementShouldBeCalledOnOriginalMessage() {
            underTest.retain(1337);

            verify(originalMessage).retain(1337);
        }

        @Test
        void touchShouldBeCalledOnOriginalMessage() {
            underTest.touch();

            verify(originalMessage).touch();
        }

        @Test
        void touchWithHintShouldBeCalledOnOriginalMessage(@Mock final Object hint) {
            underTest.touch(hint);

            verify(originalMessage).touch(hint);
        }

        @Test
        void releaseShouldBeCalledOnOriginalMessage() {
            when(underTest.refCnt()).thenReturn(1);
            underTest.release();

            verify(originalMessage).release();
        }

        @Test
        void releaseWithDecrementShouldBeCalledOnOriginalMessage() {
            underTest.release(1337);

            verify(originalMessage).release(1337);
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldNotFail() {
            try {
                final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

                assertNotNull(envelope.toString());
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
            }
        }
    }

    @Nested
    class GetId {
        @Test
        void shouldReturnId() {
            try {
                final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

                assertEquals(messageId, envelope.getId());
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
            }
        }

        @Test
        void shouldThrowIllegalArgumentExceptionOnError() throws IOException {
            try {
                final IntermediateEnvelope<MessageLite> envelope = spy(IntermediateEnvelope.of(message));
                when(envelope.getPublicHeader()).thenThrow(IOException.class);

                assertThrows(IllegalArgumentException.class, envelope::getId);
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
            }
        }
    }

    @Nested
    class GetUserAgent {
        @Test
        void shouldReturnUserAgent() {
            try {
                final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

                assertEquals(UserAgent.generate(), envelope.getUserAgent());
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
            }
        }

        @Test
        void shouldThrowIllegalArgumentExceptionOnError() throws IOException {
            try {
                final IntermediateEnvelope<MessageLite> envelope = spy(IntermediateEnvelope.of(message));
                when(envelope.getPublicHeader()).thenThrow(IOException.class);

                assertThrows(IllegalArgumentException.class, envelope::getUserAgent);
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
            }
        }
    }

    @Nested
    class GetNetworkId {
        @Test
        void shouldReturnNetworkId() {
            try {
                final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

                assertEquals(1, envelope.getNetworkId());
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
            }
        }

        @Test
        void shouldThrowIllegalArgumentExceptionOnError() throws IOException {
            try {
                final IntermediateEnvelope<MessageLite> envelope = spy(IntermediateEnvelope.of(message));
                when(envelope.getPublicHeader()).thenThrow(IOException.class);

                assertThrows(IllegalArgumentException.class, envelope::getNetworkId);
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
            }
        }
    }

    @Nested
    class GetProofOfWork {
        @Test
        void shouldReturnProofOfWork() {
            try {
                final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

                assertEquals(ProofOfWork.of(6657650), envelope.getProofOfWork());
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
            }
        }

        @Test
        void shouldThrowIllegalArgumentExceptionOnError() throws IOException {
            try {
                final IntermediateEnvelope<MessageLite> envelope = spy(IntermediateEnvelope.of(message));
                when(envelope.getPublicHeader()).thenThrow(IOException.class);

                assertThrows(IllegalArgumentException.class, envelope::getProofOfWork);
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
            }
        }
    }

    @Nested
    class GetRecipient {
        @Test
        void shouldReturnRecipient() {
            try {
                final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

                assertEquals(recipientPublicKey, envelope.getRecipient());
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
            }
        }

        @Test
        void shouldThrowIllegalArgumentExceptionOnError() throws IOException {
            try {
                final IntermediateEnvelope<MessageLite> envelope = spy(IntermediateEnvelope.of(message));
                when(envelope.getPublicHeader()).thenThrow(IOException.class);

                assertThrows(IllegalArgumentException.class, envelope::getRecipient);
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
            }
        }
    }

    @Nested
    class GetSender {
        @Test
        void shouldThrowIllegalArgumentExceptionOnError() throws IOException {
            try {
                final IntermediateEnvelope<MessageLite> envelope = spy(IntermediateEnvelope.of(message));
                when(envelope.getPublicHeader()).thenThrow(IOException.class);

                assertThrows(IllegalArgumentException.class, envelope::getSender);
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
            }
        }
    }

    @Nested
    class GetHopCount {
        @Test
        void shouldReturnHopCount() {
            try {
                final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

                assertEquals((byte) 0, envelope.getHopCount());
            }
            finally {
                ReferenceCountUtil.release(message);
            }
        }

        @Test
        void shouldThrowIllegalArgumentExceptionOnError() throws IOException {
            try {
                final IntermediateEnvelope<MessageLite> envelope = spy(IntermediateEnvelope.of(message));
                when(envelope.getPublicHeader()).thenThrow(IOException.class);

                assertThrows(IllegalArgumentException.class, envelope::getHopCount);
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
            }
        }
    }

    @Nested
    class GetSignature {
        @Test
        void shouldReturnSignature() {
            try {
                final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);

                assertEquals(new Signature(new byte[]{}), envelope.getSignature());
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
            }
        }

        @Test
        void shouldThrowIllegalArgumentExceptionOnError() throws IOException {
            try {
                final IntermediateEnvelope<MessageLite> envelope = spy(IntermediateEnvelope.of(message));
                when(envelope.getPublicHeader()).thenThrow(IOException.class);

                assertThrows(IllegalArgumentException.class, envelope::getSignature);
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
            }
        }
    }

    @Nested
    class Arm {
        @Test
        void shouldReturnSignedMessage() throws IOException {
            final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);
            final IntermediateEnvelope<MessageLite> armedEnvelop = envelope.armAndRelease(senderPrivateKey);

            try {
                assertNotNull(armedEnvelop.getPublicHeader().getSignature());
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
                ReferenceCountUtil.safeRelease(armedEnvelop);
            }
        }

        @Test
        void getPrivatHeaderShouldFailOnArmedMessage() {
            final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);
            final IntermediateEnvelope<MessageLite> armedEnvelop = envelope.armAndRelease(senderPrivateKey);

            try {
                assertThrows(IOException.class, armedEnvelop::getPrivateHeader);
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
                ReferenceCountUtil.safeRelease(armedEnvelop);
            }
        }

        @Test
        void getBodyShouldFailOnArmedMessage() {
            final IntermediateEnvelope<MessageLite> envelope = IntermediateEnvelope.of(message);
            final IntermediateEnvelope<MessageLite> armedEnvelop = envelope.armAndRelease(senderPrivateKey);

            try {
                assertThrows(IOException.class, armedEnvelop::getBody);
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
                ReferenceCountUtil.safeRelease(armedEnvelop);
            }
        }
    }

    @Nested
    class Disarm {
        private IntermediateEnvelope<MessageLite> envelope;
        private IntermediateEnvelope<MessageLite> armedEnvelop;

        @BeforeEach
        void setUp() {
            envelope = IntermediateEnvelope.of(message);
            armedEnvelop = envelope.armAndRelease(senderPrivateKey);
        }

        @Test
        void shouldReturnDisarmedMessageIfSignatureIsValid() {
            final IntermediateEnvelope<MessageLite> disarmedMessage = armedEnvelop.disarmAndRelease(senderPrivateKey);

            try {
                assertNotNull(disarmedMessage);
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
                ReferenceCountUtil.safeRelease(armedEnvelop);
                ReferenceCountUtil.safeRelease(disarmedMessage);
            }
        }

        @Test
        void shouldThrowExceptionIfSignatureIsNotValid() {
            final IntermediateEnvelope<MessageLite> rearmed = envelope.armAndRelease(recipientPrivateKey);
            try {
                // arm with wrong private key
                assertThrows(IllegalStateException.class, () -> rearmed.disarmAndRelease(recipientPrivateKey));
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
                ReferenceCountUtil.safeRelease(armedEnvelop);
                ReferenceCountUtil.safeRelease(rearmed);
            }
        }

        @Test
        @Disabled("Encryption not implemented yet")
        void shouldThrowExceptionIfWrongPrivatKeyIsGiven() {
            try {
                assertThrows(IllegalStateException.class, () -> armedEnvelop.disarmAndRelease(recipientPrivateKey));
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
                ReferenceCountUtil.safeRelease(armedEnvelop);
            }
        }

        @Test
        void getPrivatHeaderShouldNotFailOnDisarmedMessage() throws IOException {
            final IntermediateEnvelope<MessageLite> disarmedEnvelope = armedEnvelop.disarmAndRelease(recipientPrivateKey);

            try {
                assertNotNull(disarmedEnvelope.getPrivateHeader());
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
                ReferenceCountUtil.safeRelease(armedEnvelop);
                ReferenceCountUtil.safeRelease(disarmedEnvelope);
            }
        }

        @Test
        void getBodyShouldNotFailOnDisarmedMessage() throws IOException {
            final IntermediateEnvelope<MessageLite> disarmedEnvelope = armedEnvelop.disarmAndRelease(recipientPrivateKey);

            try {
                assertNotNull(disarmedEnvelope.getBodyAndRelease());
            }
            finally {
                ReferenceCountUtil.safeRelease(message);
                ReferenceCountUtil.safeRelease(disarmedEnvelope);
            }
        }
    }

    @Nested
    class TestAcknowledgement {
        @Test
        void shouldCreateEnvelopeWithAcknowledgementMessage() throws IOException {
            final IntermediateEnvelope<Acknowledgement> acknowledgement = IntermediateEnvelope.acknowledgement(1, senderPublicKey, senderProofOfWork, recipientPublicKey, messageId);

            assertEquals(1, acknowledgement.getPublicHeader().getNetworkId());
            assertEquals(ACKNOWLEDGEMENT, acknowledgement.getPrivateHeader().getType());
            assertEquals(ByteString.copyFrom(messageId.byteArrayValue()), acknowledgement.getBodyAndRelease().getCorrespondingId());
        }
    }

    @Nested
    class TestApplication {
        @Test
        void shouldCreateEnvelopeWithApplicationMessage() throws IOException {
            final IntermediateEnvelope<Application> application = IntermediateEnvelope.application(1, senderPublicKey, senderProofOfWork, recipientPublicKey, String.class.getName(), new byte[]{});

            assertEquals(1, application.getPublicHeader().getNetworkId());
            assertEquals(APPLICATION, application.getPrivateHeader().getType());
            assertEquals(String.class.getName(), application.getBodyAndRelease().getType());
        }
    }

    @Nested
    class TestDiscovery {
        @Test
        void shouldCreateEnvelopeWithDiscoveryMessage() throws IOException {
            final IntermediateEnvelope<Discovery> discovery = IntermediateEnvelope.discovery(1, senderPublicKey, senderProofOfWork, recipientPublicKey, 1337L);

            assertEquals(1, discovery.getPublicHeader().getNetworkId());
            assertEquals(DISCOVERY, discovery.getPrivateHeader().getType());
            assertEquals(1337L, discovery.getBodyAndRelease().getChildrenTime());
        }
    }

    @Nested
    class TestUnite {
        @Test
        void shouldCreateEnvelopeWithDiscoveryMessage() throws IOException {
            final IntermediateEnvelope<Unite> unite = IntermediateEnvelope.unite(1, senderPublicKey, senderProofOfWork, recipientPublicKey, senderPublicKey, new InetSocketAddress(22527));

            assertEquals(1, unite.getPublicHeader().getNetworkId());
            assertEquals(UNITE, unite.getPrivateHeader().getType());
            assertEquals(ByteString.copyFrom(senderPublicKey.byteArrayValue()), unite.getBodyAndRelease().getPublicKey());
        }
    }
}