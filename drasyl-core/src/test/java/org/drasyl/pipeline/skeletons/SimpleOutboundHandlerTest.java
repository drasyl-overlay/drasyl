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
package org.drasyl.pipeline.skeletons;

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.MessageId;
import org.drasyl.peer.connection.message.UserAgent;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.codec.ApplicationMessage2ObjectHolderHandler;
import org.drasyl.pipeline.codec.DefaultCodec;
import org.drasyl.pipeline.codec.ObjectHolder;
import org.drasyl.pipeline.codec.ObjectHolder2ApplicationMessageHandler;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.pipeline.skeletons.SimpleOutboundHandler;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimpleOutboundHandlerTest {
    @Mock
    private Identity identity;
    @Mock
    private ProofOfWork proofOfWork;
    private DrasylConfig config;
    private final int networkId = 1;

    @BeforeEach
    void setUp() {
        config = DrasylConfig.newBuilder().build();
    }

    @Test
    void shouldTriggerOnMatchedMessage() {
        final CompressedPublicKey sender = mock(CompressedPublicKey.class);
        when(identity.getPublicKey()).thenReturn(sender);
        final byte[] payload = new byte[]{};
        final CompressedPublicKey recipient = mock(CompressedPublicKey.class);

        final SimpleOutboundHandler<byte[], CompressedPublicKey> handler = new SimpleOutboundHandler<>() {
            @Override
            protected void matchedWrite(final HandlerContext ctx,
                                        final CompressedPublicKey recipient,
                                        final byte[] msg,
                                        final CompletableFuture<Void> future) {
                // Emit this message as inbound message to test
                ctx.pipeline().processInbound(new ApplicationMessage(networkId, identity.getPublicKey(), proofOfWork, recipient, msg));
            }
        };

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                config,
                identity,
                TypeValidator.ofInboundValidator(config),
                TypeValidator.ofOutboundValidator(config),
                ApplicationMessage2ObjectHolderHandler.INSTANCE,
                ObjectHolder2ApplicationMessageHandler.INSTANCE,
                DefaultCodec.INSTANCE, handler);
        final TestObserver<Pair<Address, Object>> inboundMessageTestObserver = pipeline.inboundMessages().test();
        final TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundOnlyMessages(ApplicationMessage.class).test();

        pipeline.processOutbound(recipient, payload);

        inboundMessageTestObserver.awaitCount(1).assertValueCount(1);
        inboundMessageTestObserver.assertValue(Pair.of(sender, payload));
        outboundMessageTestObserver.assertNoValues();
    }

    @Test
    void shouldPassthroughsNotMatchingMessage() {
        final SimpleOutboundHandler<MyMessage, CompressedPublicKey> handler = new SimpleOutboundHandler<>(MyMessage.class, CompressedPublicKey.class) {
            @Override
            protected void matchedWrite(final HandlerContext ctx,
                                        final CompressedPublicKey recipient,
                                        final MyMessage msg,
                                        final CompletableFuture<Void> future) {
                // Emit this message as inbound message to test
                ctx.pipeline().processInbound(msg);
            }
        };

        final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                config,
                identity,
                TypeValidator.ofInboundValidator(config),
                TypeValidator.ofOutboundValidator(config),
                ApplicationMessage2ObjectHolderHandler.INSTANCE,
                ObjectHolder2ApplicationMessageHandler.INSTANCE,
                DefaultCodec.INSTANCE, handler);
        final TestObserver<Pair<Address, Object>> inboundMessageTestObserver = pipeline.inboundMessages().test();
        final TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundOnlyMessages(ApplicationMessage.class).test();

        final CompressedPublicKey sender = mock(CompressedPublicKey.class);
        when(identity.getPublicKey()).thenReturn(sender);
        final ProofOfWork senderProofOfWork = mock(ProofOfWork.class);
        when(identity.getProofOfWork()).thenReturn(senderProofOfWork);
        final CompressedPublicKey recipient = mock(CompressedPublicKey.class);
        final byte[] payload = new byte[]{};
        pipeline.processOutbound(recipient, payload);

        outboundMessageTestObserver.awaitCount(1).assertValueCount(1);
        outboundMessageTestObserver.assertValue(new ApplicationMessage(networkId, sender, senderProofOfWork, recipient, Map.of(ObjectHolder.CLASS_KEY_NAME, payload.getClass().getName()), payload));
        inboundMessageTestObserver.assertNoValues();
    }

    static class MyMessage implements Message {
        @Override
        public MessageId getId() {
            return null;
        }

        @Override
        public UserAgent getUserAgent() {
            return null;
        }

        @Override
        public int getNetworkId() {
            return 0;
        }

        @Override
        public CompressedPublicKey getSender() {
            return null;
        }

        @Override
        public ProofOfWork getProofOfWork() {
            return null;
        }

        @Override
        public CompressedPublicKey getRecipient() {
            return null;
        }

        @Override
        public short getHopCount() {
            return 0;
        }

        @Override
        public void incrementHopCount() {

        }
    }
}