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

import io.netty.buffer.ByteBuf;
import io.netty.channel.nio.NioEventLoopGroup;
import io.reactivex.rxjava3.exceptions.UndeliverableException;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.drasyl.DrasylConfig;
import org.drasyl.crypto.CryptoException;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.HandlerAdapter;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.remote.protocol.Protocol.Application;
import org.drasyl.util.ReferenceCountUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrasylPipelineIT {
    private PublishSubject<Event> receivedEvents;
    private PublishSubject<Object> outboundMessages;
    private Pipeline pipeline;
    private Identity identity1;
    private Identity identity2;
    private byte[] payload;

    @BeforeEach
    void setup() throws CryptoException {
        receivedEvents = PublishSubject.create();
        outboundMessages = PublishSubject.create();

        identity1 = Identity.of(169092, "030a59784f88c74dcd64258387f9126739c3aeb7965f36bb501ff01f5036b3d72b", "0f1e188d5e3b98daf2266d7916d2e1179ae6209faa7477a2a66d4bb61dab4399");
        identity2 = Identity.of(26778671, "0236fde6a49564a0eaa2a7d6c8f73b97062d5feb36160398c08a5b73f646aa5fe5", "093d1ee70518508cac18eaf90d312f768c14d43de9bfd2618a2794d8df392da0");

        payload = new byte[]{
                0x01,
                0x02,
                0x03
        };

        final DrasylConfig config = DrasylConfig.newBuilder()
                .networkId(0)
                .identityProofOfWork(identity1.getProofOfWork())
                .identityPublicKey(identity1.getPublicKey())
                .identityPrivateKey(identity1.getPrivateKey())
                .build();

        final PeersManager peersManager = new PeersManager(receivedEvents::onNext, identity1);
        final AtomicBoolean started = new AtomicBoolean(true);
        pipeline = new DrasylPipeline(receivedEvents::onNext, config, identity1, peersManager, started, new NioEventLoopGroup());
        pipeline.addFirst("outboundMessages", new SimpleOutboundHandler<>() {
            @Override
            protected void matchedWrite(final HandlerContext ctx,
                                        final Address recipient,
                                        final Object msg,
                                        final CompletableFuture<Void> future) {
                if (!future.isDone()) {
                    outboundMessages.onNext(msg);
                    future.complete(null);
                }
            }
        });
    }

    @Test
    void passMessageThroughThePipeline() {
        final TestObserver<Event> events = receivedEvents.test();

        final byte[] newPayload = new byte[]{
                0x05
        };

        IntStream.range(0, 10).forEach(i -> pipeline.addLast("handler" + i, new HandlerAdapter()));

        pipeline.addLast("msgChanger", new HandlerAdapter() {
            @Override
            public void read(final HandlerContext ctx,
                             final Address sender,
                             final Object msg,
                             final CompletableFuture<Void> future) {
                super.read(ctx, identity2.getPublicKey(), newPayload, future);
            }
        });

        final IntermediateEnvelope<Application> message = IntermediateEnvelope.application(0, identity2.getPublicKey(), identity2.getProofOfWork(), identity1.getPublicKey(), byte[].class.getName(), new byte[]{}).armAndRelease(identity2.getPrivateKey());

        pipeline.processInbound(message.getSender(), message);

        events.awaitCount(1).assertValueCount(1);
        events.assertValue(new MessageEvent(identity2.getPublicKey(), newPayload));

        ReferenceCountUtil.safeRelease(message);
    }

    @Test
    void passEventThroughThePipeline() {
        final TestObserver<Event> events = receivedEvents.test();

        final Event testEvent = new Event() {
        };

        IntStream.range(0, 10).forEach(i -> pipeline.addLast("handler" + i, new HandlerAdapter()));

        pipeline.addLast("eventProducer", new HandlerAdapter() {
            @Override
            public void read(final HandlerContext ctx,
                             final Address sender,
                             final Object msg,
                             final CompletableFuture<Void> future) {
                super.read(ctx, sender, msg, future);
                ctx.fireEventTriggered(testEvent, new CompletableFuture<>());
            }
        });

        final IntermediateEnvelope<Application> message = IntermediateEnvelope.application(0, identity2.getPublicKey(), identity2.getProofOfWork(), identity1.getPublicKey(), byte[].class.getName(), "Hallo Welt".getBytes()).armAndRelease(identity2.getPrivateKey());

        pipeline.processInbound(message.getSender(), message);

        events.awaitCount(2);
        events.assertValueAt(0, new MessageEvent(message.getSender(), "Hallo Welt".getBytes()));
        events.assertValueAt(1, testEvent);

        ReferenceCountUtil.safeRelease(message);
    }

    @Test
    void exceptionShouldPassThroughThePipeline() {
        final PublishSubject<Throwable> receivedExceptions = PublishSubject.create();
        final TestObserver<Throwable> exceptions = receivedExceptions.test();

        final RuntimeException exception = new RuntimeException("Error!");
        RxJavaPlugins.setErrorHandler(e -> {
            assertThat(e, instanceOf(UndeliverableException.class));
            assertThat(e.getCause(), instanceOf(PipelineException.class));
            assertThat(e.getCause().getCause(), instanceOf(RuntimeException.class));
            assertEquals(exception.getMessage(), e.getCause().getCause().getMessage());
        });

        IntStream.range(0, 10).forEach(i -> pipeline.addLast("handler" + i, new HandlerAdapter()));

        pipeline.addFirst("exceptionCatcher", new HandlerAdapter() {
            @Override
            public void exceptionCaught(final HandlerContext ctx, final Exception cause) {
                exceptions.onNext(cause);
                super.exceptionCaught(ctx, cause);
            }
        });

        pipeline.addFirst("exceptionProducer", new HandlerAdapter() {
            @Override
            public void read(final HandlerContext ctx,
                             final Address sender,
                             final Object msg,
                             final CompletableFuture<Void> future) {
                super.read(ctx, sender, msg, future);
                throw exception;
            }
        });

        final IntermediateEnvelope<Application> message = IntermediateEnvelope.application(0, identity2.getPublicKey(), identity2.getProofOfWork(), identity1.getPublicKey(), byte[].class.getName(), new byte[]{}).armAndRelease(identity2.getPrivateKey());

        pipeline.processInbound(message.getSender(), message);

        exceptions.awaitCount(1).assertValueCount(1);
        exceptions.assertValue(exception);

        ReferenceCountUtil.safeRelease(message);
    }

    @Test
    void passOutboundThroughThePipeline() {
        final TestObserver<Object> outbounds = outboundMessages.test();

        final byte[] newPayload = new byte[]{
                0x05
        };

        IntStream.range(0, 10).forEach(i -> pipeline.addLast("handler" + i, new HandlerAdapter()));

        pipeline.addLast("outboundChanger", new HandlerAdapter() {
            @Override
            public void write(final HandlerContext ctx,
                              final Address recipient,
                              final Object msg,
                              final CompletableFuture<Void> future) {
                super.write(ctx, identity2.getPublicKey(), newPayload, future);
            }
        });

        final CompletableFuture<Void> future = pipeline.processOutbound(identity1.getPublicKey(), payload);

        outbounds.awaitCount(1).assertValueCount(1);
        outbounds.assertValue(m -> m instanceof ByteBuf);
        future.join();
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertFalse(future.isCompletedExceptionally());
    }

    @Test
    void shouldNotPassthroughsMessagesWithDoneFuture() {
        final TestObserver<Object> outbounds = outboundMessages.test();
        final IntermediateEnvelope<Application> msg = IntermediateEnvelope.application(0, identity1.getPublicKey(), identity1.getProofOfWork(), identity2.getPublicKey(), byte[].class.getName(), payload);

        IntStream.range(0, 10).forEach(i -> pipeline.addLast("handler" + i, new HandlerAdapter()));

        pipeline.addLast("outbound", new HandlerAdapter() {
            @Override
            public void write(final HandlerContext ctx,
                              final Address recipient,
                              final Object msg,
                              final CompletableFuture<Void> future) {
                future.complete(null);
                super.write(ctx, recipient, msg, future);
            }
        });

        final CompletableFuture<Void> future = pipeline.processOutbound(identity2.getPublicKey(), msg);

        future.join();
        outbounds.assertNoValues();
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertFalse(future.isCompletedExceptionally());

        ReferenceCountUtil.safeRelease(msg);
    }

    @Test
    void shouldNotPassthroughsMessagesWithExceptionallyFuture() {
        final TestObserver<Object> outbounds = outboundMessages.test();
        final IntermediateEnvelope<Application> msg = IntermediateEnvelope.application(0, identity1.getPublicKey(), identity1.getProofOfWork(), identity2.getPublicKey(), byte[].class.getName(), payload);

        IntStream.range(0, 10).forEach(i -> pipeline.addLast("handler" + i, new HandlerAdapter()));

        pipeline.addLast("outbound", new HandlerAdapter() {
            @Override
            public void write(final HandlerContext ctx,
                              final Address recipient,
                              final Object msg,
                              final CompletableFuture<Void> future) {
                future.completeExceptionally(new Exception("Error!"));
                super.write(ctx, recipient, msg, future);
            }
        });

        final CompletableFuture<Void> future = pipeline.processOutbound(identity2.getPublicKey(), msg);

        assertThrows(CompletionException.class, future::join);
        outbounds.assertNoValues();
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertTrue(future.isCompletedExceptionally());

        ReferenceCountUtil.safeRelease(msg);
    }
}
