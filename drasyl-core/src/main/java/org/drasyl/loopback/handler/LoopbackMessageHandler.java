/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.loopback.handler;

import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Stateless;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.message.ApplicationMessage;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;

import java.util.concurrent.CompletableFuture;

/**
 * This handler converts outgoing messages addressed to the local node to incoming messages
 * addressed to the local node.
 */
@Stateless
public class LoopbackMessageHandler extends SimpleOutboundHandler<ApplicationMessage, Address> {
    public static final String LOOPBACK_MESSAGE_HANDLER = "LOOPBACK_OUTBOUND_MESSAGE_SINK_HANDLER";
    private boolean started;

    LoopbackMessageHandler(final boolean started) {
        this.started = started;
    }

    public LoopbackMessageHandler() {
        this(false);
    }

    @Override
    public void eventTriggered(final HandlerContext ctx,
                               final Event event,
                               final CompletableFuture<Void> future) {
        if (event instanceof NodeUpEvent) {
            started = true;
        }
        else if (event instanceof NodeUnrecoverableErrorEvent || event instanceof NodeDownEvent) {
            started = false;
        }

        // passthrough event
        ctx.fireEventTriggered(event, future);
    }

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final Address recipient,
                                final ApplicationMessage msg,
                                final CompletableFuture<Void> future) {
        if (started && ctx.identity().getPublicKey().equals(msg.getRecipient())) {
            ctx.fireRead(msg.getSender(), msg, future);
        }
        else {
            ctx.write(recipient, msg, future);
        }
    }
}
