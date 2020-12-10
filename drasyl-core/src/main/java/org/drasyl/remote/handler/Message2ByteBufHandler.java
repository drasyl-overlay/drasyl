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
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.remote.message.RemoteMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.util.JSONUtil.JACKSON_WRITER;
import static org.drasyl.util.LoggingUtil.sanitizeLogArg;

/**
 * Handler that converts a given {@link RemoteMessage} to a {@link ByteBuf}.
 */
public class Message2ByteBufHandler extends SimpleOutboundHandler<RemoteMessage, Address> {
    public static final Message2ByteBufHandler INSTANCE = new Message2ByteBufHandler();
    public static final String MESSAGE_2_BYTE_BUF_HANDLER = "MESSAGE_2_BYTE_BUF_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(Message2ByteBufHandler.class);

    private Message2ByteBufHandler() {
    }

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final Address recipient,
                                final RemoteMessage msg,
                                final CompletableFuture<Void> future) {

        ByteBuf byteBuf = null;
        try {
            byteBuf = PooledByteBufAllocator.DEFAULT.buffer();
            final OutputStream outputStream = new ByteBufOutputStream(byteBuf);
            JACKSON_WRITER.writeValue(outputStream, msg);

            write(ctx, recipient, byteBuf, future);
        }
        catch (final IOException e) {
            byteBuf.release();
            LOG.error("Unable to serialize '{}': {}", sanitizeLogArg(msg), e.getMessage());
            future.completeExceptionally(new Exception("Message could not be serialized. This could indicate a bug in drasyl.", e));
        }
    }
}