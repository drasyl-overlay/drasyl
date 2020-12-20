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
import io.netty.buffer.PooledByteBufAllocator;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.remote.protocol.MessageId;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.drasyl.util.FutureUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * This handler is responsible for merging incoming message chunks into a single message as well as
 * splitting outgoing too large messages into chunks.
 */
@SuppressWarnings({ "java:S110" })
public class ChunkingHandler extends SimpleDuplexHandler<IntermediateEnvelope<? extends MessageLite>, IntermediateEnvelope<? extends MessageLite>, Address> {
    public static final int MTU = 1400;
    public static final int MAX_MESSAGE_SIZE = 14_000; // must not be more than 256 times MTU!
    public static final String CHUNKING_HANDLER = "CHUNKING_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(ChunkingHandler.class);
    private static final Map<MessageId, ArrayList<ByteBuf>> chunks = new HashMap<>(); // FIXME: remove orphane chunks after some time

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final IntermediateEnvelope<? extends MessageLite> msg,
                               final CompletableFuture<Void> future) {
        try {
            // message is addressed to me and chunked
            if (ctx.identity().getPublicKey().equals(msg.getRecipient()) && msg.isChunk()) {
                handleInboundChunk(ctx, sender, msg, future);
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

    private void handleInboundChunk(final HandlerContext ctx,
                                    final Address sender,
                                    final IntermediateEnvelope<? extends MessageLite> chunk,
                                    final CompletableFuture<Void> future) throws IOException {
        final short totalChunks = chunk.getTotalChunks();
        final ArrayList<ByteBuf> messageChunks = chunks.computeIfAbsent(chunk.getId(), k -> new ArrayList<>(totalChunks));
        messageChunks.add(chunk.getChunkNo(), chunk.getInternalByteBuf());

        if (messageChunks.size() == totalChunks) { // FIXME: only use totalChunks from Header-Chunk
            // we have all chunks, build message
            final ByteBuf messageByteBuf = PooledByteBufAllocator.DEFAULT.buffer();
            for (final ByteBuf byteBuf : messageChunks) {
                messageByteBuf.writeBytes(byteBuf);
                byteBuf.release();
            }
            chunks.remove(chunk.getId());

            final IntermediateEnvelope<MessageLite> message = IntermediateEnvelope.of(messageByteBuf);
            ctx.fireRead(sender, message, future);
        }
        else {
            // other chunks missing, but this chunk has been processed
            future.complete(null);
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
                    LOG.debug("The message `{}` has a size of {} bytes and is too large. The max allowed size is {} bytes. Message dropped.", msg, messageSize, MAX_MESSAGE_SIZE);
                    future.completeExceptionally(new Exception("The message has a size of " + messageSize + " bytes and is too large. The max. allowed size is " + MAX_MESSAGE_SIZE + " bytes. Message dropped."));
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
            future.completeExceptionally(new Exception("Unable to read message", e));
            LOG.debug("Can't read message `{}` due to the following error: ", msg, e);
        }
    }

    private void chunkMessage(final HandlerContext ctx,
                              final Address recipient,
                              final IntermediateEnvelope<? extends MessageLite> msg,
                              final CompletableFuture<Void> future,
                              final ByteBuf messageByteBuf,
                              final int messageSize) throws IOException {
        try {
            final short totalChunks = (short) (messageSize / MTU + 1);
            LOG.debug("The message `{}` has a size of {} bytes and must be splitted to {} chunks (MTU = {}).", msg, messageSize, totalChunks, MTU);
            final CompletableFuture<Void>[] chunkFutures = new CompletableFuture[totalChunks];

            // create & send chunks
            final PublicHeader msgPublicHeader = msg.getPublicHeader();
            short chunkNo = 0;
            while (messageByteBuf.readableBytes() > 0) {
                ByteBuf chunkPayload = null;
                try {
                    final PublicHeader chunkHeader = PublicHeader.newBuilder()
                            .setId(msgPublicHeader.getId())
                            .setUserAgent(msgPublicHeader.getUserAgent())
                            .setSender(msgPublicHeader.getSender()) // TODO: required?
                            .setProofOfWork(msgPublicHeader.getProofOfWork()) // TODO: required?
                            .setRecipient(msgPublicHeader.getRecipient())
                            .setHopCount(ByteString.copyFrom(new byte[]{ (byte) 0 }))
                            .setTotalChunks(ByteString.copyFrom(new byte[]{ (byte) totalChunks })) // TODO: set only on first chunk?
                            .setChunkNo(ByteString.copyFrom(new byte[]{ (byte) chunkNo }))
                            .build();
                    chunkPayload = messageByteBuf.readBytes(Math.min(messageByteBuf.readableBytes(), MTU)); // TODO: use messageByteBuf.slice?

                    // send
                    final IntermediateEnvelope<MessageLite> chunk = IntermediateEnvelope.of(chunkHeader, chunkPayload);
                    chunkFutures[chunkNo] = new CompletableFuture<>();
                    ctx.write(recipient, chunk, chunkFutures[chunkNo]);

                    chunkNo++;
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
