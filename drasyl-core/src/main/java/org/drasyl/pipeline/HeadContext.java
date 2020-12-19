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

import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.DrasylConfig;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Special class that represents the head of a {@link Pipeline}. This class can not be removed from
 * the pipeline.
 */
@SuppressWarnings({ "common-java:DuplicatedBlocks" })
class HeadContext extends AbstractEndHandler {
    public static final String DRASYL_HEAD_HANDLER = "DRASYL_HEAD_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(HeadContext.class);

    public HeadContext(final DrasylConfig config,
                       final Pipeline pipeline,
                       final Scheduler scheduler,
                       final Identity identity,
                       final PeersManager peersManager,
                       final TypeValidator inboundValidator,
                       final TypeValidator outboundValidator) {
        super(DRASYL_HEAD_HANDLER, config, pipeline, scheduler, identity, peersManager, inboundValidator, outboundValidator);
    }

    @Override
    public void handlerAdded(final HandlerContext ctx) {
        LOG.debug("Pipeline head was added.");
    }

    @Override
    public void handlerRemoved(final HandlerContext ctx) {
        LOG.debug("Pipeline head was removed.");
    }

    @Override
    public void write(final HandlerContext ctx,
                      final Address recipient,
                      final Object msg,
                      final CompletableFuture<Void> future) {
        try {
            if (msg instanceof AutoSwallow) {
                future.complete(null);
                return;
            }

            if (future.isDone()) {
                LOG.debug("Message `{}` with recipient `{}` has arrived at the end of the pipeline and was already completed.", msg, recipient);
            }
            else {
                LOG.warn("Message `{}` with recipient `{}` has arrived at the end of the pipeline and was not consumed before by a handler. Therefore the message was dropped.\n" +
                        "This can happen due to a missing codec. You can find more information regarding this here: " +
                        "https://docs.drasyl.org/configuration/marshalling/", msg, recipient);
                future.completeExceptionally(new IllegalStateException("Message has arrived at the end of the pipeline and was not consumed before by a handler. Therefore the message was dropped. This can happen due to a missing codec. You can find more information regarding this here: https://docs.drasyl.org/configuration/marshalling/"));
            }
        }
        finally {
            ReferenceCountUtil.safeRelease(msg);
        }
    }
}