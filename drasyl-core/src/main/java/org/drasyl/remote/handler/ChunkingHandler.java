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
import org.drasyl.DrasylConfig;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.remote.protocol.MessageId;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.drasyl.util.FutureUtil;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.UnsignedShort;
import org.drasyl.util.Worm;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
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
    private final Worm<Map<MessageId, ChunksCollector>> chunksCollectors;

    public ChunkingHandler() {
        this.chunksCollectors = Worm.of();
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
            ReferenceCountUtil.safeRelease(msg);
        }
    }

    private void handleInboundChunk(final HandlerContext ctx,
                                    final Address sender,
                                    final IntermediateEnvelope<? extends MessageLite> chunk,
                                    final CompletableFuture<Void> future) throws IOException {
        try {
            final ChunksCollector chunksCollector = getChunksCollectors(ctx.config()).computeIfAbsent(chunk.getId(), id -> new ChunksCollector(ctx.config().getRemoteMessageMaxContentLength(), id));
            final IntermediateEnvelope<? extends MessageLite> message = chunksCollector.addChunk(chunk);

            if (message != null) {
                // message complete, pass it inbound
                getChunksCollectors(ctx.config()).remove(chunk.getId());
                ctx.fireRead(sender, message, future);
            }
            else {
                // other chunks missing, but this chunk has been processed
                future.complete(null);
            }
        }
        catch (final IllegalStateException e) {
            getChunksCollectors(ctx.config()).remove(chunk.getId());
            ReferenceCountUtil.safeRelease(chunk);
            throw e;
        }
    }

    private Map<MessageId, ChunksCollector> getChunksCollectors(final DrasylConfig config) {
        return chunksCollectors.getOrCompute(() -> CacheBuilder.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(config.getRemoteMessageComposedMessageTransferTimeout())
                .removalListener((RemovalListener<MessageId, ChunksCollector>) entry -> {
                    LOG.debug("Not all chunks of message `{}` were received within {}ms. Message dropped.", entry::getKey, config.getRemoteMessageComposedMessageTransferTimeout()::toMillis);
                    entry.getValue().release();
                })
                .build()
                .asMap());
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
                final int messageMaxContentLength = ctx.config().getRemoteMessageMaxContentLength();
                if (messageSize > messageMaxContentLength) {
                    LOG.debug("The message `{}` has a size of {} bytes and is too large. The max allowed size is {} bytes. Message dropped.", msg, messageSize, messageMaxContentLength);
                    future.completeExceptionally(new Exception("The message has a size of " + messageSize + " bytes and is too large. The max. allowed size is " + messageMaxContentLength + " bytes. Message dropped."));
                    ReferenceCountUtil.safeRelease(messageByteBuf);
                }
                else if (messageSize > ctx.config().getRemoteMessageMtu()) {
                    // message is too big, we have to chunk it
                    chunkMessage(ctx, recipient, msg, future, messageByteBuf, messageSize);
                }
                else {
                    // message is small enough. No chunking required
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
            ReferenceCountUtil.safeRelease(msg);
        }
    }

    @SuppressWarnings("unchecked")
    private void chunkMessage(final HandlerContext ctx,
                              final Address recipient,
                              final IntermediateEnvelope<? extends MessageLite> msg,
                              final CompletableFuture<Void> future,
                              final ByteBuf messageByteBuf,
                              final int messageSize) throws IOException {
        try {
            final int mtu = ctx.config().getRemoteMessageMtu();
            final UnsignedShort totalChunks = UnsignedShort.of((messageSize / mtu + 1));
            LOG.debug("The message `{}` has a size of {} bytes and must be split to {} chunks (MTU = {}).", msg, messageSize, totalChunks, mtu);
            final CompletableFuture<Void>[] chunkFutures = new CompletableFuture[totalChunks.getValue()];

            // create & send chunks
            final PublicHeader msgPublicHeader = msg.getPublicHeader();
            UnsignedShort chunkNo = UnsignedShort.of(0);
            while (messageByteBuf.readableBytes() > 0) {
                ByteBuf chunkPayload = null;
                try {
                    final PublicHeader.Builder builder = PublicHeader.newBuilder()
                            .setId(msgPublicHeader.getId())
                            .setUserAgent(msgPublicHeader.getUserAgent())
                            .setSender(msgPublicHeader.getSender())
                            .setRecipient(msgPublicHeader.getRecipient())
                            .setHopCount(ByteString.copyFrom(new byte[]{ (byte) 0 }));

                    if (chunkNo.getValue() == 0) {
                        // set only on first chunk (head chunk)
                        builder.setTotalChunks(ByteString.copyFrom(totalChunks.toBytes()));
                    }
                    else {
                        // set on all non-head chunks
                        builder.setChunkNo(ByteString.copyFrom(chunkNo.toBytes()));
                    }

                    final PublicHeader chunkHeader = builder
                            .build();
                    // use zero-copy
                    chunkPayload = messageByteBuf.readRetainedSlice(Math.min(messageByteBuf.readableBytes(), mtu));

                    // send
                    final IntermediateEnvelope<MessageLite> chunk = IntermediateEnvelope.of(chunkHeader, chunkPayload);
                    chunkFutures[chunkNo.getValue()] = new CompletableFuture<>();
                    ctx.write(recipient, chunk, chunkFutures[chunkNo.getValue()]);

                    chunkNo = chunkNo.increment();
                }
                finally {
                    ReferenceCountUtil.safeRelease(chunkPayload);
                }
            }

            FutureUtil.completeOnAllOf(future, chunkFutures);
        }
        finally {
            ReferenceCountUtil.safeRelease(messageByteBuf);
        }
    }
}
