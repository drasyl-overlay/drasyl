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

import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Stateless;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.util.LoggingUtil.sanitizeLogArg;

/**
 * Handler that converts a given {@link IntermediateEnvelope} to a {@link ByteBuf}.
 */
@Stateless
public class Message2ByteBufHandler extends SimpleOutboundHandler<IntermediateEnvelope<MessageLite>, Address> {
    public static final Message2ByteBufHandler INSTANCE = new Message2ByteBufHandler();
    public static final String MESSAGE_2_BYTE_BUF_HANDLER = "MESSAGE_2_BYTE_BUF_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(Message2ByteBufHandler.class);

    private Message2ByteBufHandler() {
    }

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final Address recipient,
                                final IntermediateEnvelope<MessageLite> msg,
                                final CompletableFuture<Void> future) {
        ByteBuf byteBuf = null;
        try {
            byteBuf = msg.getOrBuildByteBuf();

            write(ctx, recipient, byteBuf, future);
        }
        catch (final IOException e) {
            ReferenceCountUtil.safeRelease(byteBuf);
            LOG.error("Unable to serialize '{}': {}", () -> sanitizeLogArg(msg), e::getMessage);
            future.completeExceptionally(new Exception("Message could not be serialized. This could indicate a bug in drasyl.", e));
        }
    }
}
