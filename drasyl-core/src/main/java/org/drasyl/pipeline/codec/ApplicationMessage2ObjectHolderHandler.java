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
package org.drasyl.pipeline.codec;

import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Stateless;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.message.ApplicationMessage;
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;

import java.util.concurrent.CompletableFuture;

/**
 * Handler that converts a given {@link ApplicationMessage} to an {@link ObjectHolder}.
 */
@Stateless
public class ApplicationMessage2ObjectHolderHandler extends SimpleInboundHandler<ApplicationMessage, Address> {
    public static final ApplicationMessage2ObjectHolderHandler INSTANCE = new ApplicationMessage2ObjectHolderHandler();
    public static final String APP_MSG2OBJECT_HOLDER = "applicationMessage2ObjectHolderHandler";

    private ApplicationMessage2ObjectHolderHandler() {
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final ApplicationMessage msg,
                               final CompletableFuture<Void> future) {
        ctx.fireRead(msg.getSender(), msg.getContent(), future);
    }
}
