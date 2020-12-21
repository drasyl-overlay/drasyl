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
import io.netty.buffer.PooledByteBufAllocator;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.remote.protocol.MessageId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * This class collects the individual chunks of a message and returns the message once all chunks
 * have been collected.
 */
class ChunksCollector {
    private static final Logger LOG = LoggerFactory.getLogger(ChunksCollector.class);
    private final int maxContentLength;
    private final MessageId messageId;
    private final Map<Short, ByteBuf> chunks;
    private int messageSize = 0;
    private int totalChunks = 0;

    public ChunksCollector(final int maxContentLength, final MessageId messageId) {
        this.maxContentLength = maxContentLength;
        this.messageId = requireNonNull(messageId);
        this.chunks = new HashMap<>();
    }

    /**
     * @param chunk chunk to collect
     * @return the message if all chunks were collected, otherwise {@code null}
     * @throws IOException           if chunk could not be read
     * @throws IllegalStateException if an attempt is made to add a chunk from another message or to
     *                               an already composed message
     */
    public synchronized IntermediateEnvelope<? extends MessageLite> addChunk(final IntermediateEnvelope<? extends MessageLite> chunk) throws IOException {
        // already composed?
        if (allChunksPresent()) {
            throw new IllegalStateException("All chunks have already been collected and message has already been returned");
        }

        // is chunk?
        if (!chunk.isChunk()) {
            throw new IllegalStateException("This is not a chunk!");
        }

        // belongs to our message?
        if (!chunk.getId().equals(messageId)) {
            throw new IllegalStateException("This chunk belongs to another message!");
        }

        final int chunkSize = chunk.getInternalByteBuf().readableBytes();
        final short chunkNo = chunk.getChunkNo();

        // add chunk
        if (messageSize + chunkSize > maxContentLength) {
            chunk.release();
            chunks.values().forEach(ByteBuf::release);
            LOG.debug("The chunked message with id `{}` has exhausted the max allowed size of {} bytes and was therefore dropped (tried to allocate additional {} bytes).", messageId, maxContentLength, chunkSize);
            throw new IllegalStateException("The chunked message with id `" + messageId + "` has exhausted the max allowed size of " + maxContentLength + " bytes and was therefore dropped (tried to allocate additional " + chunkSize + " bytes).");
        }
        messageSize += chunkSize;
        chunks.putIfAbsent(chunkNo, chunk.getInternalByteBuf());

        // head chunk? set totalChunks
        if (totalChunks == 0 && chunk.getTotalChunks() > 0) {
            totalChunks = chunk.getTotalChunks();
        }

        // message complete?
        if (allChunksPresent()) {
            // message complete, compose it!
            final ByteBuf messageByteBuf = PooledByteBufAllocator.DEFAULT.buffer();
            for (short i = 0; i < totalChunks; i++) {
                final ByteBuf chunkByteBuf = chunks.remove(i);
                messageByteBuf.writeBytes(chunkByteBuf);
                chunkByteBuf.release();
            }
            return IntermediateEnvelope.of(messageByteBuf);
        }
        else {
            // message not complete, return null!
            return null;
        }
    }

    private boolean allChunksPresent() {
        return totalChunks > 0 && chunks.size() == totalChunks;
    }
}
