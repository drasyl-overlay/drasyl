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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import io.netty.buffer.ByteBuf;
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
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * This handler is responsible for merging incoming message chunks into a single message as well as
 * splitting outgoing too large messages into chunks.
 */
@SuppressWarnings({ "java:S110" })
public class ChunkingHandler extends SimpleDuplexHandler<IntermediateEnvelope<? extends MessageLite>, IntermediateEnvelope<? extends MessageLite>, Address> {
    public static final String CHUNKING_HANDLER = "CHUNKING_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(ChunkingHandler.class);
    private final int mtu;
    private final int maxContentLength;
    private final Map<MessageId, ChunksCollector> chunksCollectors;

    public ChunkingHandler(final int mtu,
                           final int maxContentLength,
                           final Duration composedMessageTransferTimeout) {
        this.mtu = mtu;
        this.maxContentLength = maxContentLength;
        chunksCollectors = CacheBuilder.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(composedMessageTransferTimeout)
                .removalListener((RemovalListener<MessageId, ChunksCollector>) notification -> notification.getValue().release())
                .build()
                .asMap();
    }

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
        final ChunksCollector chunksCollector = chunksCollectors.computeIfAbsent(chunk.getId(), id -> new ChunksCollector(maxContentLength, id));
        final IntermediateEnvelope<? extends MessageLite> message = chunksCollector.addChunk(chunk);

        if (message != null) {
            // message complete, pass it inbound
            chunksCollectors.remove(chunk.getId());
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
                if (messageSize > maxContentLength) {
                    LOG.debug("The message `{}` has a size of {} bytes and is too large. The max allowed size is {} bytes. Message dropped.", msg, messageSize, maxContentLength);
                    future.completeExceptionally(new Exception("The message has a size of " + messageSize + " bytes and is too large. The max. allowed size is " + maxContentLength + " bytes. Message dropped."));
                    messageByteBuf.release();
                }
                else if (messageSize > mtu) {
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
            final short totalChunks = (short) (messageSize / mtu + 1);
            LOG.debug("The message `{}` has a size of {} bytes and must be split to {} chunks (MTU = {}).", msg, messageSize, totalChunks, mtu);
            final CompletableFuture<Void>[] chunkFutures = new CompletableFuture[totalChunks];

            // create & send chunks
            final PublicHeader msgPublicHeader = msg.getPublicHeader();
            short chunkNo = 0;
            while (messageByteBuf.readableBytes() > 0) {
                ByteBuf chunkPayload = null;
                try {
                    final PublicHeader.Builder builder = PublicHeader.newBuilder()
                            .setId(msgPublicHeader.getId())
                            .setUserAgent(msgPublicHeader.getUserAgent())
                            .setSender(msgPublicHeader.getSender()) // TODO: required?
                            .setProofOfWork(msgPublicHeader.getProofOfWork()) // TODO: required?
                            .setRecipient(msgPublicHeader.getRecipient())
                            .setHopCount(ByteString.copyFrom(new byte[]{ (byte) 0 }));

                    if (chunkNo == 0) {
                        // set only on first chunk (head chunk)
                        builder.setTotalChunks(ByteString.copyFrom(new byte[]{ (byte) totalChunks }));
                    }
                    else {
                        // set on all non-head chunks
                        builder.setChunkNo(ByteString.copyFrom(new byte[]{ (byte) chunkNo }));
                    }

                    final PublicHeader chunkHeader = builder
                            .build();
                    chunkPayload = messageByteBuf.readBytes(Math.min(messageByteBuf.readableBytes(), mtu)); // TODO: use messageByteBuf.slice?

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
