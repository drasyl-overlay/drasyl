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
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.drasyl.remote.protocol.IntermediateEnvelope;
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
    private static final Logger LOG = LoggerFactory.getLogger(ChunkingHandler.class);
    private static final int MTU = 1400;
    private static final int MAX_MESSAGE_SIZE = 14_000;

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final IntermediateEnvelope<? extends MessageLite> msg,
                               final CompletableFuture<Void> future) {

        try {
            if (ctx.identity().getPublicKey().equals(msg.getRecipient())) {
                // message is addressed to me, check if it is a fragment
                final boolean isFragment = msg.getPublicHeader().getFragment();
                if (isFragment) {
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
                /**
                 * FIXME:
                 * - check message size is bigger than {@link #MTU} bytes
                 * - if too big, get and remove first {@link #MTU} from message, create chunk and call {@code ctx.write(...)}
                 * - repeat previous step until complete message has been consumed.
                 * - if not too big, pass message to {@code ctx.write(...)}
                 */
            }
            else {
                ctx.write(recipient, msg, future);
            }
        }
        catch (final IllegalStateException e) {
            future.completeExceptionally(new Exception("Unable to arm message", e));
            LOG.debug("Can't arm message `{}` due to the following error: ", msg, e);
        }
    }
}
