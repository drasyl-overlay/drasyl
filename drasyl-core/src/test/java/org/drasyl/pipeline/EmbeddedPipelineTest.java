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
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.codec.ApplicationMessage2ObjectHolderHandler;
import org.drasyl.pipeline.codec.DefaultCodec;
import org.drasyl.pipeline.codec.ObjectHolder;
import org.drasyl.pipeline.codec.ObjectHolder2ApplicationMessageHandler;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.pipeline.message.ApplicationMessage;
import org.drasyl.pipeline.skeleton.HandlerAdapter;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddedPipelineTest {
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    private DrasylConfig config;

    @BeforeEach
    void setUp() {
        config = DrasylConfig.newBuilder()
                .networkId(1)
                .build();
    }

    @Test
    void shouldReturnInboundMessagesAndEvents() {
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                config,
                identity,
                peersManager,
                TypeValidator.ofInboundValidator(config),
                TypeValidator.of(List.of(), List.of(), false, false),
                ApplicationMessage2ObjectHolderHandler.INSTANCE,
                ObjectHolder2ApplicationMessageHandler.INSTANCE,
                DefaultCodec.INSTANCE,
                new HandlerAdapter(),
                new HandlerAdapter());
        final TestObserver<Pair<Address, Object>> inboundMessageTestObserver = pipeline.inboundMessages().test();
        final TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundOnlyMessages(ApplicationMessage.class).test();
        final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

        final CompressedPublicKey sender = mock(CompressedPublicKey.class);
        final ApplicationMessage msg = mock(ApplicationMessage.class);

        when(msg.getSender()).thenReturn(sender);
        when(msg.getContent()).thenReturn(ObjectHolder.of(String.class.getName(), new byte[]{
                34,
                72,
                101,
                108,
                108,
                111,
                32,
                87,
                111,
                114,
                108,
                100,
                34
        }));

        pipeline.processInbound(msg.getSender(), msg);

        inboundMessageTestObserver.awaitCount(1).assertValueCount(1);
        inboundMessageTestObserver.assertValue(Pair.of(sender, "Hello World"));
        eventTestObserver.awaitCount(1).assertValueCount(1);
        eventTestObserver.assertValue(new MessageEvent(sender, "Hello World"));
        outboundMessageTestObserver.assertNoValues();
        pipeline.close();
    }

    @Test
    void shouldReturnOutboundMessages() {
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                config,
                identity,
                peersManager, TypeValidator.of(List.of(), List.of(), false, false),
                TypeValidator.ofOutboundValidator(config),
                ObjectHolder2ApplicationMessageHandler.INSTANCE,
                DefaultCodec.INSTANCE,
                new HandlerAdapter(),
                new HandlerAdapter()
        );
        final TestObserver<Pair<Address, Object>> inboundMessageTestObserver = pipeline.inboundMessages().test();
        final TestObserver<ApplicationMessage> outboundMessageTestObserver = pipeline.outboundOnlyMessages(ApplicationMessage.class).test();
        final TestObserver<Event> eventTestObserver = pipeline.inboundEvents().test();

        final CompressedPublicKey sender = mock(CompressedPublicKey.class);
        final CompressedPublicKey recipient = mock(CompressedPublicKey.class);
        when(identity.getPublicKey()).thenReturn(sender);
        final byte[] msg = new byte[]{};
        pipeline.processOutbound(recipient, msg);

        outboundMessageTestObserver.awaitCount(1).assertValueCount(1);
        outboundMessageTestObserver.assertValue(new ApplicationMessage(sender, recipient, ObjectHolder.of(byte[].class, msg)));
        inboundMessageTestObserver.assertNoValues();
        eventTestObserver.assertNoValues();
        pipeline.close();
    }
}