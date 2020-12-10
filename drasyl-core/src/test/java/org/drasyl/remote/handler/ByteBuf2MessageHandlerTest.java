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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.remote.message.RemoteApplicationMessage;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class ByteBuf2MessageHandlerTest {
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

    @Test
    void shouldConvertByteBufToMessage(@Mock final Address sender) {
        final String json = "{\"@type\":\"" + RemoteApplicationMessage.class.getSimpleName() + "\",\"id\":\"QSF2lStbgf0T+Ep8\",\"networkId\":1,\"sender\":\"AwlE0gLOX/DubfAUgtIkzL7HJGWt3I5FeO3uqlmX9RG7\",\"proofOfWork\":6657650,\"recipient\":\"Az3j2mmfb5/71CfFZyWRBlW6ORO+T/VbE8Yo6VfIYP1V\",\"payload\":\"AAEC\",\"userAgent\":\"\"}";
        final ByteBuf byteBuf = Unpooled.wrappedBuffer(json.getBytes());

        final ByteBuf2MessageHandler handler = ByteBuf2MessageHandler.INSTANCE;
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
        final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();
        pipeline.processInbound(sender, byteBuf);

        inboundMessages.awaitCount(1).assertValueCount(1);
        inboundMessages.assertValue(pair -> pair.second() instanceof RemoteApplicationMessage);
    }

    @Test
    void shouldFailOnInvalidMessage(@Mock final Address sender) {
        final String json = "{}";
        final ByteBuf byteBuf = Unpooled.wrappedBuffer(json.getBytes());

        final ByteBuf2MessageHandler handler = ByteBuf2MessageHandler.INSTANCE;
        final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);
        final TestObserver<Pair<Address, Object>> inboundMessages = pipeline.inboundMessages().test();
        final CompletableFuture<Void> future = pipeline.processInbound(sender, byteBuf);

        assertThrows(ExecutionException.class, future::get);
        inboundMessages.assertNoValues();
    }
}