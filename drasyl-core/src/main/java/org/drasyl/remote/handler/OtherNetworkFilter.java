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
import org.drasyl.pipeline.Stateless;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;
import org.drasyl.remote.protocol.IntermediateEnvelope;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;

import static org.drasyl.util.LoggingUtil.sanitizeLogArg;

/**
 * This handler filters out all messages received from other networks.
 */
@Stateless
public class OtherNetworkFilter extends SimpleInboundHandler<IntermediateEnvelope<MessageLite>, Address> {
    public static final OtherNetworkFilter INSTANCE = new OtherNetworkFilter();
    public static final String OTHER_NETWORK_FILTER = "OTHER_NETWORK_FILTER";
    private static final Logger LOG = LoggerFactory.getLogger(OtherNetworkFilter.class);

    private OtherNetworkFilter() {
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final IntermediateEnvelope<MessageLite> msg,
                               final CompletableFuture<Void> future) {
        try {
            if (ctx.config().getNetworkId() == msg.getNetworkId()) {
                ctx.fireRead(sender, msg, future);
            }
            else {
                LOG.trace("Message from other network dropped: {}", msg);
                future.completeExceptionally(new Exception("Message from other network dropped"));
            }
        }
        catch (final IllegalArgumentException e) {
            LOG.error("Unable to read network id from message '{}': {}", sanitizeLogArg(msg), e.getMessage());
            ReferenceCountUtil.safeRelease(msg);
            future.completeExceptionally(new Exception("Unable to read network id from message.", e));
        }
    }
}
