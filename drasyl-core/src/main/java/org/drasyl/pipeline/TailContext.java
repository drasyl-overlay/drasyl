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
package org.drasyl.pipeline;

import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.serialization.Serialization;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.scheduler.DrasylScheduler;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Special class that represents the tail of a {@link Pipeline}. This class can not be removed from
 * the pipeline.
 */
class TailContext extends AbstractEndHandler {
    public static final String DRASYL_TAIL_HANDLER = "DRASYL_TAIL_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(TailContext.class);
    private final Consumer<Event> eventConsumer;

    @SuppressWarnings({ "java:S107" })
    public TailContext(final Consumer<Event> eventConsumer,
                       final DrasylConfig config,
                       final Pipeline pipeline,
                       final DrasylScheduler dependentScheduler,
                       final DrasylScheduler independentScheduler,
                       final Identity identity,
                       final PeersManager peersManager,
                       final Serialization inboundSerialization,
                       final Serialization outboundSerialization) {
        super(DRASYL_TAIL_HANDLER, config, pipeline, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
        this.eventConsumer = eventConsumer;
    }

    @Override
    public void handlerAdded(final HandlerContext ctx) {
        LOG.debug("Pipeline tail was added.");
    }

    @Override
    public void handlerRemoved(final HandlerContext ctx) {
        LOG.debug("Pipeline tail was removed.");
    }

    @Override
    public void read(final HandlerContext ctx,
                     final Address sender,
                     final Object msg,
                     final CompletableFuture<Void> future) {
        if (msg instanceof AutoSwallow) {
            future.complete(null);
            return;
        }

        // Pass message to Application
        if (future.isDone()) {
            LOG.warn("Message `{}` was not written to the application, because the corresponding future was already completed.", msg);
        }
        else if (sender instanceof CompressedPublicKey) {
            final CompressedPublicKey senderAddress = (CompressedPublicKey) sender;
            final MessageEvent event = MessageEvent.of(senderAddress, msg);
            future.complete(null);
            LOG.trace("Event has passed the pipeline: `{}` ", event);

            eventConsumer.accept(event);
        }
        else {
            LOG.debug("Message '{}' was not written to the application, because the corresponding address was not of type {} (was type {}).", () -> msg, CompressedPublicKey.class::getSimpleName, sender.getClass()::getSimpleName);
        }
    }

    @Override
    public void eventTriggered(final HandlerContext ctx,
                               final Event event,
                               final CompletableFuture<Void> future) {
        // Pass event to Application
        if (future.isDone()) {
            LOG.warn("Event `{}` was not written to the application, because the corresponding future was already completed.", event);
        }
        else {
            future.complete(null);
            LOG.trace("Event has passed the pipeline: `{}` ", event);

            eventConsumer.accept(event);
        }
    }
}
