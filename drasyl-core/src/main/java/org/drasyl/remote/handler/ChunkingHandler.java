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

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.drasyl.util.FutureUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * This handler is responsible for merging incoming message fragments into a single message as well
 * as splitting outgoing too large messages into fragments.
 */
@SuppressWarnings({ "java:S110" })
public class ChunkingHandler extends SimpleDuplexHandler<IntermediateEnvelope<? extends MessageLite>, IntermediateEnvelope<? extends MessageLite>, Address> {
    public static final int MTU = 1400;
    public static final int MAX_MESSAGE_SIZE = 14_000; // must not be more than 256 times MTU!
    private static final Logger LOG = LoggerFactory.getLogger(ChunkingHandler.class);

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final IntermediateEnvelope<? extends MessageLite> msg,
                               final CompletableFuture<Void> future) {

        try {
            if (ctx.identity().getPublicKey().equals(msg.getRecipient())) {
                // message is addressed to me, check if it is a fragment
                if (msg.isFragment()) {
                    /**
                     * FIXME:
                     * - cache fragment
                     * - check if all message fragments are available and then build message and call {@code ctx.fireRead(...)}
                     * - drop cached fragments of incomplete messages after some time
                     */
                }
                else {
                    // passthrough non-fragmented message
                    ctx.fireRead(sender, msg, future);
                }
            }
            else {
                // passthrough all messages not addressed to us
                ctx.fireRead(sender, msg, future);
            }
        }
        catch (final IllegalStateException | IOException e) {
            future.completeExceptionally(new Exception("Unable to read message", e));
            LOG.debug("Can't read message `{}` due to the following error: ", msg, e);
        }
    }

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final Address recipient,
                                final IntermediateEnvelope<? extends MessageLite> msg,
                                final CompletableFuture<Void> future) {
        try {
            if (ctx.identity().getPublicKey().equals(msg.getSender())) {
                // message from us, check if we have to chunk it
                final ByteBuf messageByteBuf = msg.getOrBuildByteBuf();
                final int messageSize = messageByteBuf.readableBytes();
                if (messageSize > MAX_MESSAGE_SIZE) {
                    future.completeExceptionally(new Exception("The message has a size of " + messageSize + " bytes and is too large. The max. allowed size is " + MAX_MESSAGE_SIZE + " bytes."));
                    messageByteBuf.release();
                }
                else if (messageSize > MTU) {
                    // message is too big, we have to chunk it
                    chunkMessage(ctx, recipient, msg, future, messageByteBuf, messageSize);
                }
                else {
                    // message is small enough. no chunking required
                    ctx.write(recipient, msg, future);
                }
            }
            else {
                ctx.write(recipient, msg, future);
            }
        }
        catch (final IllegalStateException | IOException e) {
            future.completeExceptionally(new Exception("Unable to arm message", e));
            LOG.debug("Can't arm message `{}` due to the following error: ", msg, e);
        }
    }

    private void chunkMessage(final HandlerContext ctx,
                              final Address recipient,
                              final IntermediateEnvelope<? extends MessageLite> msg,
                              final CompletableFuture<Void> future,
                              final ByteBuf messageByteBuf,
                              final int messageSize) throws IOException {
        try {
            final PublicHeader msgPublicHeader = msg.getPublicHeader();
            final short totalFragments = (short) (messageSize / MTU + 1);
            final CompletableFuture<Void>[] chunkFutures = new CompletableFuture[totalFragments];

            // create & send chunks
            short fragmentNo = 0;
            while (messageByteBuf.readableBytes() > 0) {
                ByteBuf chunkPayload = null;
                try {
                    final PublicHeader chunkHeader = PublicHeader.newBuilder()
                            .setId(msgPublicHeader.getId())
                            .setUserAgent(msgPublicHeader.getUserAgent())
                            .setRecipient(msgPublicHeader.getRecipient())
                            .setHopCount(ByteString.copyFrom(new byte[]{ (byte) 0 }))
                            .setTotalFragments(ByteString.copyFrom(new byte[]{ (byte) totalFragments }))
                            .setFragmentNo(ByteString.copyFrom(new byte[]{ (byte) fragmentNo }))
                            .build();
                    chunkPayload = messageByteBuf.readBytes(Math.min(messageByteBuf.readableBytes(), MTU));

                    // send
                    final IntermediateEnvelope<MessageLite> chunk = IntermediateEnvelope.of(chunkHeader, chunkPayload);
                    chunkFutures[fragmentNo] = new CompletableFuture<>();
                    ctx.write(recipient, chunk, chunkFutures[fragmentNo]);

                    fragmentNo++;
                }
                finally {
                    if (chunkPayload != null && chunkPayload.refCnt() != 0) {
                        chunkPayload.release();
                    }
                }
            }

            FutureUtil.completeOnAllOf(future, chunkFutures);
        }
        finally {
            messageByteBuf.release();
        }
    }
}
