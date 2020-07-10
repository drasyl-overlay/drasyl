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
package org.drasyl.pipeline;

import org.drasyl.peer.connection.message.ApplicationMessage;

import java.util.concurrent.CompletableFuture;

/**
 * {@link Handler} implementation which represents a combination out of a {@link InboundHandler} and
 * the {@link OutboundHandler}.
 * <p>
 * It is a good starting point if your {@link Handler} implementation needs to intercept operations
 * and also state updates.
 */
public class DuplexHandler extends InboundHandlerAdapter implements OutboundHandler {
    @Override
    public void write(HandlerContext ctx,
                      ApplicationMessage msg,
                      CompletableFuture<Void> future) {
        ctx.write(msg, future);
    }
}