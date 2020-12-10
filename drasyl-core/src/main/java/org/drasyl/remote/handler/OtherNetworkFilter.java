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

import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;
import org.drasyl.remote.message.RemoteMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * This handler filters out all messages received from other networks.
 */
public class OtherNetworkFilter extends SimpleInboundHandler<RemoteMessage, Address> {
    public static final OtherNetworkFilter INSTANCE = new OtherNetworkFilter();
    public static final String OTHER_NETWORK_FILTER = "OTHER_NETWORK_FILTER";
    private static final Logger LOG = LoggerFactory.getLogger(OtherNetworkFilter.class);

    private OtherNetworkFilter() {
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final RemoteMessage msg,
                               final CompletableFuture<Void> future) {
        if (ctx.config().getNetworkId() == msg.getNetworkId()) {
            ctx.fireRead(sender, msg, future);
        }
        else {
            LOG.trace("Message from other network dropped: {}", msg);
            future.completeExceptionally(new Exception("Message from other network dropped"));
        }
    }
}