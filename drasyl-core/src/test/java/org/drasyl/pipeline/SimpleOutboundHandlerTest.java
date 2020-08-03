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
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.ChunkedMessage;
import org.drasyl.pipeline.codec.DefaultCodec;
import org.drasyl.pipeline.codec.ObjectHolder2ApplicationMessageHandler;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimpleOutboundHandlerTest {
    @Mock
    private Identity identity;
    private DrasylConfig config;

    @BeforeEach
    void setUp() {
        config = DrasylConfig.newBuilder().build();
    }

    @Test
    void shouldTriggerOnMatchedMessage() {
        CompressedPublicKey sender = mock(CompressedPublicKey.class);
        when(identity.getPublicKey()).thenReturn(sender);
        byte[] payload = new byte[]{};
        CompressedPublicKey recipient = mock(CompressedPublicKey.class);

        SimpleOutboundHandler<byte[]> handler = new SimpleOutboundHandler<>() {
            @Override
            protected void matchedWrite(HandlerContext ctx,
                                        CompressedPublicKey recipient,
                                        byte[] msg,
                                        CompletableFuture<Void> future) {
                // Emit this message as inbound message to test
                ctx.pipeline().processInbound(new ApplicationMessage(identity.getPublicKey(), recipient, msg, msg.getClass()));
            }
        };

        EmbeddedPipeline pipeline = new EmbeddedPipeline(identity, TypeValidator.of(config), ObjectHolder2ApplicationMessageHandler.INSTANCE, DefaultCodec.INSTANCE, handler);
        TestObserver<Pair<CompressedPublicKey, Object>> inboundMessageTestObserver = pipeline.inboundMessages().test();
        TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages().test();

        pipeline.processOutbound(recipient, payload);

        inboundMessageTestObserver.awaitCount(1);
        inboundMessageTestObserver.assertValue(Pair.of(sender, payload));
        outboundMessageTestObserver.assertNoValues();
    }

    @Test
    void shouldPassthroughsNotMatchingMessage() {
        SimpleOutboundHandler<ChunkedMessage> handler = new SimpleOutboundHandler<>(ChunkedMessage.class) {
            @Override
            protected void matchedWrite(HandlerContext ctx,
                                        CompressedPublicKey recipient,
                                        ChunkedMessage msg,
                                        CompletableFuture<Void> future) {
                // Emit this message as inbound message to test
                ctx.pipeline().processInbound(msg);
            }
        };

        EmbeddedPipeline pipeline = new EmbeddedPipeline(identity, TypeValidator.of(config), ObjectHolder2ApplicationMessageHandler.INSTANCE, DefaultCodec.INSTANCE, handler);
        TestObserver<Pair<CompressedPublicKey, Object>> inboundMessageTestObserver = pipeline.inboundMessages().test();
        TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundMessages().test();

        CompressedPublicKey sender = mock(CompressedPublicKey.class);
        CompressedPublicKey recipient = mock(CompressedPublicKey.class);
        when(identity.getPublicKey()).thenReturn(sender);
        byte[] payload = new byte[]{};
        pipeline.processOutbound(recipient, payload);

        outboundMessageTestObserver.awaitCount(1);
        outboundMessageTestObserver.assertValue(new ApplicationMessage(sender, recipient, payload, byte[].class));
        inboundMessageTestObserver.assertNoValues();
    }
}