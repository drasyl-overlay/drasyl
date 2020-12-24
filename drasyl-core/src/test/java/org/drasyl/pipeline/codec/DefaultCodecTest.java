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
package org.drasyl.pipeline.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.message.ApplicationMessage;
import org.drasyl.util.JSONUtil;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultCodecTest {
    @Mock
    private HandlerContext ctx;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock
    private CompressedPublicKey sender;
    @Mock
    private ProofOfWork proofOfWork;
    @Mock
    private CompressedPublicKey recipient;
    @Mock
    private Consumer<Object> passOnConsumer;
    private DrasylConfig config;
    private final int networkId = 1;

    @BeforeEach
    void setUp() {
        config = DrasylConfig.newBuilder().build();
    }

    @Nested
    class Encode {
        @Test
        void shouldSkippByteArrays() {
            final byte[] msg = new byte[]{};
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                    config,
                    identity,
                    peersManager,
                    TypeValidator.ofOutboundValidator(config),
                    TypeValidator.of(List.of(), List.of(), false, false),
                    ApplicationMessage2ObjectHolderHandler.INSTANCE,
                    ObjectHolder2ApplicationMessageHandler.INSTANCE,
                    DefaultCodec.INSTANCE);
            final TestObserver<ApplicationMessage> testObserver = pipeline.outboundOnlyMessages(ApplicationMessage.class).test();

            when(identity.getPublicKey()).thenReturn(sender);
            pipeline.processOutbound(recipient, msg);

            testObserver.awaitCount(1).assertValueCount(1);
            testObserver.assertValue(new ApplicationMessage(sender, recipient, ObjectHolder.of(byte[].class, msg)));
            pipeline.close();
        }

        @Test
        void passthroughsOnNotSerializiableMessages() {
            final StringBuilder msg = new StringBuilder();

            when(ctx.outboundValidator()).thenReturn(TypeValidator.ofOutboundValidator(config));

            DefaultCodec.INSTANCE.encode(ctx, msg, passOnConsumer);

            verify(passOnConsumer).accept(msg);
        }

        @Test
        void passthroughsOnNotSerializiableMessages2() {
            final TypeValidator validator = TypeValidator.ofOutboundValidator(config);
            validator.addClass(InputStream.class);
            final InputStream msg = mock(InputStream.class);

            when(ctx.outboundValidator()).thenReturn(TypeValidator.ofOutboundValidator(config));

            DefaultCodec.INSTANCE.encode(ctx, msg, passOnConsumer);

            verify(passOnConsumer).accept(msg);
        }

        @Test
        void shouldEncodePOJOs() throws JsonProcessingException {
            final Integer msg = Integer.valueOf("10000");
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                    config,
                    identity,
                    peersManager,
                    TypeValidator.of(List.of(), List.of(), false, false),
                    TypeValidator.ofOutboundValidator(config),
                    ApplicationMessage2ObjectHolderHandler.INSTANCE,
                    ObjectHolder2ApplicationMessageHandler.INSTANCE,
                    DefaultCodec.INSTANCE);
            final TestObserver<ApplicationMessage> testObserver = pipeline.outboundOnlyMessages(ApplicationMessage.class).test();

            when(identity.getPublicKey()).thenReturn(sender);
            final CompletableFuture<Void> future = pipeline.processOutbound(recipient, msg);

            testObserver.awaitCount(1).assertValueCount(1);
            testObserver.assertValue(new ApplicationMessage(sender, recipient, ObjectHolder.of(Integer.class.getName(), JSONUtil.JACKSON_WRITER.writeValueAsBytes(msg))));
            future.join();
            assertTrue(future.isDone());
            pipeline.close();
        }
    }

    @Nested
    class Decode {
        @Test
        void shouldSkippByteArrays() {
            final ApplicationMessage msg = new ApplicationMessage(sender, recipient, ObjectHolder.of(byte[].class, new byte[]{}));
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                    config,
                    identity,
                    peersManager,
                    TypeValidator.of(List.of(), List.of(), false, false),
                    TypeValidator.ofInboundValidator(config),
                    ApplicationMessage2ObjectHolderHandler.INSTANCE,
                    ObjectHolder2ApplicationMessageHandler.INSTANCE,
                    DefaultCodec.INSTANCE);
            final TestObserver<Pair<Address, Object>> testObserver = pipeline.inboundMessages().test();

            pipeline.processInbound(msg.getSender(), msg);

            testObserver.awaitCount(1).assertValueCount(1);
            testObserver.assertValue(Pair.of(sender, new byte[]{}));
            pipeline.close();
        }

        @Test
        void passthroughsOnNotSerializiableMessages() {
            final ObjectHolder msg = ObjectHolder.of(StringBuilder.class, new byte[]{
                    34, 34
            });

            when(ctx.inboundValidator()).thenReturn(TypeValidator.ofInboundValidator(config));
            DefaultCodec.INSTANCE.decode(ctx, msg, passOnConsumer);

            verify(passOnConsumer).accept(msg);
        }

        @Test
        void passthroughsOnNotSerializiableMessages2() {
            final TypeValidator validator = TypeValidator.ofInboundValidator(config);
            validator.addClass(Vector.class);
            final ObjectHolder msg = ObjectHolder.of(Vector.class, new byte[]{});

            when(ctx.inboundValidator()).thenReturn(validator);
            DefaultCodec.INSTANCE.decode(ctx, msg, passOnConsumer);

            verify(passOnConsumer).accept(msg);
        }

        @Test
        void passthroughsOnNotSerializiableMessages3() {
            final ObjectHolder msg = ObjectHolder.of("foo.bar.Class", new byte[]{});

            DefaultCodec.INSTANCE.decode(ctx, msg, passOnConsumer);

            verify(passOnConsumer).accept(msg);
        }

        @Test
        void shouldDecodePOJOs() throws JsonProcessingException {
            final Integer integer = Integer.valueOf("10000");
            final ApplicationMessage msg = new ApplicationMessage(sender, recipient, ObjectHolder.of(Integer.class.getName(), JSONUtil.JACKSON_WRITER.writeValueAsBytes(integer)));
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                    config,
                    identity,
                    peersManager,
                    TypeValidator.ofInboundValidator(config),
                    TypeValidator.of(List.of(), List.of(), false, false),
                    ApplicationMessage2ObjectHolderHandler.INSTANCE,
                    ObjectHolder2ApplicationMessageHandler.INSTANCE,
                    DefaultCodec.INSTANCE);
            final TestObserver<Pair<Address, Object>> testObserver = pipeline.inboundMessages().test();

            pipeline.processInbound(msg.getSender(), msg);

            testObserver.awaitCount(1).assertValueCount(1);
            testObserver.assertValue(Pair.of(sender, integer));
            pipeline.close();
        }
    }

    @Nested
    class Events {
        @Test
        void shouldPassEvents() {
            final Event event = mock(Event.class);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                    config,
                    identity,
                    peersManager,
                    TypeValidator.of(List.of(), List.of(), false, false),
                    TypeValidator.ofInboundValidator(config),
                    ApplicationMessage2ObjectHolderHandler.INSTANCE,
                    ObjectHolder2ApplicationMessageHandler.INSTANCE,
                    DefaultCodec.INSTANCE);
            final TestObserver<Event> testObserver = pipeline.inboundEvents().test();

            pipeline.processInbound(event);

            testObserver.awaitCount(1).assertValueCount(1);
            testObserver.assertValue(event);
            pipeline.close();
        }
    }
}