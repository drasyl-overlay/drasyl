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
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract {@link Pipeline} implementation, that needs head and tail.
 */
public abstract class DefaultPipeline implements Pipeline {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultPipeline.class);
    protected Map<String, AbstractHandlerContext> handlerNames;
    protected AbstractEndHandler head;
    protected AbstractEndHandler tail;
    protected Scheduler scheduler;
    protected DrasylConfig config;
    protected Identity identity;
    protected PeersManager peersManager;
    protected TypeValidator inboundValidator;
    protected TypeValidator outboundValidator;

    protected void initPointer() {
        this.head.setNextHandlerContext(this.tail);
        this.tail.setPrevHandlerContext(this.head);
        try {
            this.head.handler().handlerAdded(this.head);
            this.tail.handler().handlerAdded(this.head);
        }
        catch (final Exception e) {
            this.head.fireExceptionCaught(e);
        }
    }

    @Override
    public Pipeline addFirst(final String name, final Handler handler) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(handler);
        final AbstractHandlerContext newCtx;

        synchronized (this) {
            collisionCheck(name);

            newCtx = new DefaultHandlerContext(name, handler, config, this, scheduler, identity, peersManager, inboundValidator, outboundValidator);
            // Set correct pointer on new context
            newCtx.setPrevHandlerContext(this.head);
            newCtx.setNextHandlerContext(this.head.getNext());

            // Set correct pointer on old context
            this.head.getNext().setPrevHandlerContext(newCtx);
            this.head.setNextHandlerContext(newCtx);

            registerNewHandler(name, newCtx);
        }

        return this;
    }

    /**
     * Checks if the handler is already registered, if so an {@link IllegalArgumentException} is
     * thrown.
     *
     * @param name handler name
     * @throws IllegalArgumentException if handler is already registered
     */
    private void collisionCheck(final String name) {
        if (handlerNames.containsKey(name)) {
            throw new IllegalArgumentException("A handler with this name is already registered");
        }
    }

    private void registerNewHandler(final String name, final AbstractHandlerContext handlerCtx) {
        // Add to handlerName list
        handlerNames.put(name, handlerCtx);

        // Call handler added
        try {
            handlerCtx.handler().handlerAdded(handlerCtx);
        }
        catch (final Exception e) {
            handlerCtx.fireExceptionCaught(e);
            LOG.warn("Error on adding handler `{}`: ", handlerCtx::name, () -> e);
        }
    }

    @Override
    public Pipeline addLast(final String name, final Handler handler) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(handler);
        final AbstractHandlerContext newCtx;

        synchronized (this) {
            collisionCheck(name);

            newCtx = new DefaultHandlerContext(name, handler, config, this, scheduler, identity, peersManager, inboundValidator, outboundValidator);
            // Set correct pointer on new context
            newCtx.setPrevHandlerContext(this.tail.getPrev());
            newCtx.setNextHandlerContext(this.tail);

            // Set correct pointer on old context
            this.tail.getPrev().setNextHandlerContext(newCtx);
            this.tail.setPrevHandlerContext(newCtx);

            registerNewHandler(name, newCtx);
        }

        return this;
    }

    @Override
    public Pipeline addBefore(final String baseName, final String name, final Handler handler) {
        Objects.requireNonNull(baseName);
        Objects.requireNonNull(name);
        Objects.requireNonNull(handler);
        final AbstractHandlerContext newCtx;

        synchronized (this) {
            collisionCheck(name);

            final AbstractHandlerContext baseCtx = handlerNames.get(baseName);
            Objects.requireNonNull(baseCtx);

            newCtx = new DefaultHandlerContext(name, handler, config, this, scheduler, identity, peersManager, inboundValidator, outboundValidator);
            // Set correct pointer on new context
            newCtx.setPrevHandlerContext(baseCtx.getPrev());
            newCtx.setNextHandlerContext(baseCtx);

            // Set correct pointer on old context
            baseCtx.getPrev().setNextHandlerContext(newCtx);
            baseCtx.setPrevHandlerContext(newCtx);

            registerNewHandler(name, newCtx);
        }

        return this;
    }

    @Override
    public Pipeline addAfter(final String baseName, final String name, final Handler handler) {
        Objects.requireNonNull(baseName);
        Objects.requireNonNull(name);
        Objects.requireNonNull(handler);
        final AbstractHandlerContext newCtx;

        synchronized (this) {
            collisionCheck(name);

            final AbstractHandlerContext baseCtx = handlerNames.get(baseName);
            Objects.requireNonNull(baseCtx);

            newCtx = new DefaultHandlerContext(name, handler, config, this, scheduler, identity, peersManager, inboundValidator, outboundValidator);
            // Set correct pointer on new context
            newCtx.setPrevHandlerContext(baseCtx);
            newCtx.setNextHandlerContext(baseCtx.getNext());

            // Set correct pointer on old context
            baseCtx.getNext().setPrevHandlerContext(newCtx);
            baseCtx.setNextHandlerContext(newCtx);

            registerNewHandler(name, newCtx);
        }

        return this;
    }

    @Override
    public Pipeline remove(final String name) {
        Objects.requireNonNull(name);

        synchronized (this) {
            final AbstractHandlerContext ctx = handlerNames.remove(name);
            if (ctx == null) {
                throw new NoSuchElementException("There is no handler with this name in the pipeline");
            }

            // call remove action
            removeHandlerAction(ctx);

            final AbstractHandlerContext prev = ctx.getPrev();
            final AbstractHandlerContext next = ctx.getNext();
            prev.setNextHandlerContext(next);
            next.setPrevHandlerContext(prev);
        }

        return this;
    }

    private void removeHandlerAction(final AbstractHandlerContext ctx) {
        // call remove action
        try {
            ctx.handler().handlerRemoved(ctx);
        }
        catch (final Exception e) {
            ctx.fireExceptionCaught(e);
            LOG.warn("Error on adding handler `{}`: ", ctx::name, () -> e);
        }
    }

    @Override
    public Pipeline replace(final String oldName, final String newName, final Handler newHandler) {
        Objects.requireNonNull(oldName);
        Objects.requireNonNull(newName);
        Objects.requireNonNull(newHandler);
        final AbstractHandlerContext newCtx;

        synchronized (this) {
            if (!oldName.equals(newName)) {
                collisionCheck(newName);
            }

            final AbstractHandlerContext oldCtx = handlerNames.remove(oldName);
            final AbstractHandlerContext prev = oldCtx.getPrev();
            final AbstractHandlerContext next = oldCtx.getNext();

            // call remove action
            removeHandlerAction(oldCtx);

            newCtx = new DefaultHandlerContext(newName, newHandler, config, this, scheduler, identity, peersManager, inboundValidator, outboundValidator);
            // Set correct pointer on new context
            newCtx.setPrevHandlerContext(prev);
            newCtx.setNextHandlerContext(next);

            // Set correct pointer on old context
            prev.setNextHandlerContext(newCtx);
            next.setPrevHandlerContext(newCtx);

            registerNewHandler(newName, newCtx);
        }

        return this;
    }

    @Override
    public Handler get(final String name) {
        Objects.requireNonNull(name);

        if (handlerNames.containsKey(name)) {
            return handlerNames.get(name).handler();
        }

        return null;
    }

    @Override
    public HandlerContext context(final String name) {
        Objects.requireNonNull(name);

        return handlerNames.get(name);
    }

    @Override
    public CompletableFuture<Void> processInbound(final Address sender,
                                                  final Object msg) {
        final CompletableFuture<Void> rtn = new CompletableFuture<>();

        this.scheduler.scheduleDirect(() -> this.head.fireRead(sender, msg, rtn));

        return rtn;
    }

    @Override
    public CompletableFuture<Void> processInbound(final Event event) {
        final CompletableFuture<Void> rtn = new CompletableFuture<>();

        this.scheduler.scheduleDirect(() -> this.head.fireEventTriggered(event, rtn));

        return rtn;
    }

    @Override
    public CompletableFuture<Void> processOutbound(final Address recipient,
                                                   final Object msg) {
        final CompletableFuture<Void> rtn = new CompletableFuture<>();

        this.scheduler.scheduleDirect(() -> this.tail.write(recipient, msg, rtn));

        return rtn;
    }
}