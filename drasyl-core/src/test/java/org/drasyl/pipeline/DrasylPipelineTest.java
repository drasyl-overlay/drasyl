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

import io.netty.channel.EventLoopGroup;
import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.ApplicationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.drasyl.loopback.handler.LoopbackInboundMessageSinkHandler.LOOPBACK_INBOUND_MESSAGE_SINK_HANDLER;
import static org.drasyl.loopback.handler.LoopbackOutboundMessageSinkHandler.LOOPBACK_OUTBOUND_MESSAGE_SINK_HANDLER;
import static org.drasyl.pipeline.HeadContext.DRASYL_HEAD_HANDLER;
import static org.drasyl.pipeline.TailContext.DRASYL_TAIL_HANDLER;
import static org.drasyl.pipeline.codec.DefaultCodec.DEFAULT_CODEC;
import static org.drasyl.remote.handler.ByteBuf2MessageHandler.BYTE_BUF_2_MESSAGE_HANDLER;
import static org.drasyl.remote.handler.ChunkingHandler.CHUNKING_HANDLER;
import static org.drasyl.remote.handler.HopCountGuard.HOP_COUNT_GUARD;
import static org.drasyl.remote.handler.InvalidProofOfWorkFilter.INVALID_PROOF_OF_WORK_FILTER;
import static org.drasyl.remote.handler.Message2ByteBufHandler.MESSAGE_2_BYTE_BUF_HANDLER;
import static org.drasyl.remote.handler.OtherNetworkFilter.OTHER_NETWORK_FILTER;
import static org.drasyl.remote.handler.SignatureHandler.SIGNATURE_HANDLER;
import static org.drasyl.remote.handler.UdpDiscoveryHandler.UDP_DISCOVERY_HANDLER;
import static org.drasyl.remote.handler.UdpServer.UDP_SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DrasylPipelineTest {
    @Mock
    private Map<String, AbstractHandlerContext> handlerNames;
    @Mock
    private AbstractEndHandler head;
    @Mock
    private AbstractEndHandler tail;
    @Mock
    private Consumer<Event> eventConsumer;
    @Mock
    private Scheduler scheduler;
    @Mock
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock
    private AtomicBoolean started;
    @Mock
    private EventLoopGroup workerGroup;
    @Mock
    private Set<Endpoint> endpoints;

    @Test
    void shouldCreateNewPipeline() {
        when(config.isRemoteEnabled()).thenReturn(true);

        final Pipeline pipeline = new DrasylPipeline(eventConsumer, config, identity, peersManager, started, workerGroup);

        // Test if head and tail handlers are added
        assertNull(pipeline.get(DRASYL_HEAD_HANDLER));
        assertNull(pipeline.context(DRASYL_HEAD_HANDLER));
        assertNull(pipeline.get(DRASYL_TAIL_HANDLER));
        assertNull(pipeline.context(DRASYL_TAIL_HANDLER));

        // Test if default handler are added
        assertNotNull(pipeline.get(DEFAULT_CODEC), "This handler is required in the DrasylPipeline");
        assertNotNull(pipeline.get(HOP_COUNT_GUARD), "This handler is required in the DrasylPipeline");
        assertNotNull(pipeline.get(LOOPBACK_INBOUND_MESSAGE_SINK_HANDLER), "This handler is required in the DrasylPipeline");
        assertNotNull(pipeline.get(LOOPBACK_OUTBOUND_MESSAGE_SINK_HANDLER), "This handler is required in the DrasylPipeline");
        assertNotNull(pipeline.get(UDP_DISCOVERY_HANDLER), "This handler is required in the DrasylPipeline");
        assertNotNull(pipeline.get(SIGNATURE_HANDLER), "This handler is required in the DrasylPipeline");
        assertNotNull(pipeline.get(INVALID_PROOF_OF_WORK_FILTER), "This handler is required in the DrasylPipeline");
        assertNotNull(pipeline.get(OTHER_NETWORK_FILTER), "This handler is required in the DrasylPipeline");
        assertNotNull(pipeline.get(CHUNKING_HANDLER), "This handler is required in the DrasylPipeline");
        assertNotNull(pipeline.get(MESSAGE_2_BYTE_BUF_HANDLER), "This handler is required in the DrasylPipeline");
        assertNotNull(pipeline.get(BYTE_BUF_2_MESSAGE_HANDLER), "This handler is required in the DrasylPipeline");
        assertNotNull(pipeline.get(UDP_SERVER), "This handler is required in the DrasylPipeline");
    }

    @Test
    void shouldAddHandlerOnFirstPosition() {
        when(head.getNext()).thenReturn(tail);
        final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        final Handler handler = mock(Handler.class);

        pipeline.addFirst("name", handler);

        verify(head).setNextHandlerContext(isA(AbstractHandlerContext.class));
        verify(tail).setPrevHandlerContext(isA(AbstractHandlerContext.class));
    }

    @Test
    void shouldAddHandlerOnLastPosition() {
        when(tail.getPrev()).thenReturn(head);
        final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        final Handler handler = mock(Handler.class);

        pipeline.addLast("name", handler);

        verify(head).setNextHandlerContext(isA(AbstractHandlerContext.class));
        verify(tail).setPrevHandlerContext(isA(AbstractHandlerContext.class));
    }

    @Test
    void shouldAddHandlerBeforePosition() {
        final ArgumentCaptor<AbstractHandlerContext> captor = ArgumentCaptor.forClass(AbstractHandlerContext.class);
        final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        final AbstractHandlerContext baseCtx = mock(AbstractHandlerContext.class);
        final Handler handler = mock(Handler.class);

        when(handlerNames.get("name1")).thenReturn(baseCtx);
        when(baseCtx.getPrev()).thenReturn(head);
        pipeline.addBefore("name1", "name2", handler);

        verify(baseCtx).setPrevHandlerContext(captor.capture());
        verify(baseCtx, never()).setNextHandlerContext(any());

        assertSame(handler, captor.getValue().handler());
        assertSame(captor.getValue().getPrev(), head);
        assertSame(captor.getValue().getNext(), baseCtx);

        verify(head).setNextHandlerContext(same(captor.getValue()));
        verify(head, never()).setPrevHandlerContext(any());

        verify(captor.getValue().handler()).handlerAdded(same(captor.getValue()));
    }

    @Test
    void shouldAddHandlerAfterPosition() {
        final ArgumentCaptor<AbstractHandlerContext> captor = ArgumentCaptor.forClass(AbstractHandlerContext.class);
        final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        final AbstractHandlerContext baseCtx = mock(AbstractHandlerContext.class);
        final Handler handler = mock(Handler.class);

        when(handlerNames.get("name1")).thenReturn(baseCtx);
        when(baseCtx.getNext()).thenReturn(tail);
        pipeline.addAfter("name1", "name2", handler);

        verify(baseCtx).setNextHandlerContext(captor.capture());
        verify(tail).setPrevHandlerContext(eq(captor.getValue()));
        assertEquals(handler, captor.getValue().handler());
        verify(captor.getValue().handler()).handlerAdded(eq(captor.getValue()));
    }

    @Test
    void shouldThrowExceptionIfHandlerDoesNotExistsOnRemoveHandler() {
        final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        assertThrows(NoSuchElementException.class, () -> pipeline.remove("name"));
    }

    @Test
    void shouldRemoveHandler() {
        final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        final AbstractHandlerContext ctx = mock(AbstractHandlerContext.class);
        final Handler handler = mock(Handler.class);

        when(handlerNames.remove("name")).thenReturn(ctx);
        when(ctx.handler()).thenReturn(handler);
        when(ctx.getPrev()).thenReturn(head);
        when(ctx.getNext()).thenReturn(tail);
        pipeline.remove("name");

        verify(head).setNextHandlerContext(tail);
        verify(tail).setPrevHandlerContext(head);
        verify(handler).handlerRemoved(ctx);
    }

    @Test
    void shouldReplaceHandler() {
        final ArgumentCaptor<AbstractHandlerContext> captor1 = ArgumentCaptor.forClass(AbstractHandlerContext.class);
        final ArgumentCaptor<AbstractHandlerContext> captor2 = ArgumentCaptor.forClass(AbstractHandlerContext.class);
        final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        final AbstractHandlerContext oldCtx = mock(AbstractHandlerContext.class);
        final Handler oldHandler = mock(Handler.class);

        when(handlerNames.remove("oldName")).thenReturn(oldCtx);
        when(oldCtx.handler()).thenReturn(oldHandler);
        when(oldCtx.getPrev()).thenReturn(head);
        when(oldCtx.getNext()).thenReturn(tail);

        final Handler newHandler = mock(Handler.class);

        pipeline.replace("oldName", "newName", newHandler);

        verify(oldHandler).handlerRemoved(oldCtx);
        verify(head).setNextHandlerContext(captor1.capture());
        verify(tail).setPrevHandlerContext(captor2.capture());

        assertEquals(captor1.getValue(), captor2.getValue());
        assertEquals(newHandler, captor1.getValue().handler());

        verify(newHandler).handlerAdded(captor1.getValue());
    }

    @Test
    void shouldReturnCorrectHandler() {
        final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        final AbstractHandlerContext ctx = mock(AbstractHandlerContext.class);
        final Handler handler = mock(Handler.class);

        when(handlerNames.containsKey("name")).thenReturn(true);
        when(handlerNames.get("name")).thenReturn(ctx);
        when(ctx.handler()).thenReturn(handler);

        assertEquals(handler, pipeline.get("name"));
    }

    @Test
    void shouldReturnCorrectContext() {
        final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        final AbstractHandlerContext ctx = mock(AbstractHandlerContext.class);

        when(handlerNames.get("name")).thenReturn(ctx);

        assertEquals(ctx, pipeline.context("name"));
    }

    @Test
    void shouldExecuteInboundMessage() {
        final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        final CompressedPublicKey sender = mock(CompressedPublicKey.class);
        final ApplicationMessage msg = mock(ApplicationMessage.class);
        when(msg.getSender()).thenReturn(sender);

        final CompletableFuture<Void> future = pipeline.processInbound(msg.getSender(), msg);

        verify(scheduler).scheduleDirect(captor.capture());
        captor.getValue().run();
        verify(head).fireRead(eq(sender), eq(msg), eq(future));
    }

    @Test
    void shouldExecuteInboundEvent() {
        final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        final Event event = mock(Event.class);

        final CompletableFuture<Void> future = pipeline.processInbound(event);

        verify(scheduler).scheduleDirect(captor.capture());
        captor.getValue().run();
        verify(head).fireEventTriggered(eq(event), eq(future));
    }

    @Test
    void shouldExecuteOutboundMessage() {
        final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        final CompressedPublicKey recipient = mock(CompressedPublicKey.class);
        final AddressedEnvelope<?, ?> msg = mock(AddressedEnvelope.class);

        final CompletableFuture<Void> future = pipeline.processOutbound(recipient, msg);

        verify(scheduler).scheduleDirect(captor.capture());
        captor.getValue().run();
        verify(tail).write(eq(recipient), eq(msg), eq(future));
    }
}