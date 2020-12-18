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
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignatureHandlerTest {
    @Mock
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock
    private TypeValidator inboundValidator;
    @Mock
    private TypeValidator outboundValidator;
    @Mock
    private ProofOfWork proofOfWork;
    @Mock
    private CompressedPublicKey recipient;

    @Nested
    class OutboundMessages {
        @Test
        void shouldArmOutgoingMessageFromMe(@Mock final CompressedPublicKey recipient) throws CryptoException, IOException {
            when(identity.getPrivateKey()).thenReturn(CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
            final IntermediateEnvelope<Application> messageEnvelope = IntermediateEnvelope.application(1, identity.getPublicKey(), proofOfWork, identity.getPublicKey(), byte[].class.getName(), new byte[]{});

            final SignatureHandler handler = SignatureHandler.INSTANCE;
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
            final TestObserver<IntermediateEnvelope> outboundMessages = pipeline.outboundOnlyMessages(IntermediateEnvelope.class).test();

            pipeline.processOutbound(recipient, messageEnvelope);

            outboundMessages.awaitCount(1).assertValueCount(1);
            outboundMessages.assertValue(m -> m.getSignature().getBytes().length != 0);
        }

        @Test
        void shouldPassthroughOutgoingMessageNotFromMe(@Mock final CompressedPublicKey recipient) throws CryptoException, IOException {
            when(identity.getPrivateKey()).thenReturn(CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
            final IntermediateEnvelope<Application> messageEnvelope = IntermediateEnvelope.application(1, CompressedPublicKey.of("0248b7221b49775dcae85b02fdc9df41fbed6236c72c5c0356b59961190d3f8a13"), proofOfWork, identity.getPublicKey(), byte[].class.getName(), new byte[]{});

            final SignatureHandler handler = SignatureHandler.INSTANCE;
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
            final TestObserver<IntermediateEnvelope> outboundMessages = pipeline.outboundOnlyMessages(IntermediateEnvelope.class).test();

            pipeline.processOutbound(recipient, messageEnvelope);

            outboundMessages.awaitCount(1).assertValueCount(1);
            outboundMessages.assertValue(m -> m.getSignature().getBytes().length == 0);
        }

        @Test
        void shouldCompleteFutureExceptionallyAndNotPassOutgoingMessageIfArmingFailed() throws CryptoException, InterruptedException, IOException {
            when(identity.getPrivateKey()).thenReturn(CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
            final IntermediateEnvelope<Application> messageEnvelope = spy(IntermediateEnvelope.application(1, identity.getPublicKey(), proofOfWork, identity.getPublicKey(), byte[].class.getName(), new byte[]{}));
            when(messageEnvelope.arm(identity.getPrivateKey())).thenThrow(IllegalStateException.class);

            final SignatureHandler handler = SignatureHandler.INSTANCE;
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
            final TestObserver<IntermediateEnvelope> outboundMessages = pipeline.outboundOnlyMessages(IntermediateEnvelope.class).test();

            assertThrows(ExecutionException.class, () -> pipeline.processOutbound(recipient, messageEnvelope).get());
            outboundMessages.await(1, SECONDS);
            outboundMessages.assertNoValues();
        }
    }

    @Nested
    class InboundMessages {
        @Test
        void shouldDisarmIngoingMessageAddressedToMe(@Mock final CompressedPublicKey sender) throws CryptoException, IOException {
            when(identity.getPrivateKey()).thenReturn(CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
            final IntermediateEnvelope<Application> messageEnvelope = IntermediateEnvelope.application(1, identity.getPublicKey(), proofOfWork, identity.getPublicKey(), byte[].class.getName(), new byte[]{}).arm(identity.getPrivateKey());

            final SignatureHandler handler = SignatureHandler.INSTANCE;
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
            final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();

            pipeline.processInbound(sender, messageEnvelope);

            inboundMessages.awaitCount(1).assertValueCount(1);
            inboundMessages.assertValue(p -> p.second() instanceof IntermediateEnvelope && ((IntermediateEnvelope) p.second()).getPrivateHeader() != null);
        }

        @Test
        void shouldPassthroughIngoingMessageNotAddressedToMe(@Mock final CompressedPublicKey sender) throws CryptoException, IOException {
            when(identity.getPrivateKey()).thenReturn(CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
            final IntermediateEnvelope<Application> messageEnvelope = IntermediateEnvelope.application(1, identity.getPublicKey(), proofOfWork, CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"), byte[].class.getName(), new byte[]{}).arm(identity.getPrivateKey());

            final SignatureHandler handler = SignatureHandler.INSTANCE;
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
            final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();

            pipeline.processInbound(sender, messageEnvelope);

            inboundMessages.awaitCount(1).assertValueCount(1);
            inboundMessages.assertValue(p -> p.second().equals(messageEnvelope));
        }

        @Test
        void shouldCompleteFutureExceptionallyAndNotPassIngoingMessageIfDisarmingFailed(@Mock final CompressedPublicKey sender) throws CryptoException, InterruptedException, IOException {
            when(identity.getPrivateKey()).thenReturn(CompressedPrivateKey.of("05880bb5848fc8db0d8f30080b8c923860622a340aae55f4509d62f137707e34"));
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(16425882));
            final IntermediateEnvelope<Application> messageEnvelope = spy(IntermediateEnvelope.application(1, identity.getPublicKey(), proofOfWork, identity.getPublicKey(), byte[].class.getName(), new byte[]{}).arm(identity.getPrivateKey()));
            when(messageEnvelope.disarm(any())).thenThrow(IllegalStateException.class);

            final SignatureHandler handler = SignatureHandler.INSTANCE;
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
            final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();

            assertThrows(ExecutionException.class, () -> pipeline.processInbound(sender, messageEnvelope).get());
            inboundMessages.await(1, SECONDS);
            inboundMessages.assertNoValues();
        }
    }
}
