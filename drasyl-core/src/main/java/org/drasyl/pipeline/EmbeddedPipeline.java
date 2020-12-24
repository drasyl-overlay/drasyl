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

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.DrasylConfig;
import org.drasyl.crypto.Crypto;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.DrasylScheduler;
import org.drasyl.util.Pair;
import org.drasyl.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedded {@link Pipeline} implementation, that allows easy testing of {@link Handler}s.
 */
@SuppressWarnings({ "java:S107" })
public class EmbeddedPipeline extends DefaultPipeline implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedPipeline.class);
    private final Subject<Pair<Address, Object>> inboundMessages;
    private final Subject<Event> inboundEvents;
    private final Subject<Pair<Address, Object>> outboundMessages;

    /**
     * Creates a new embedded pipeline and adds all given handler to it. Handler are added with
     * their simple class name.
     *
     * @param config            the config
     * @param identity          the identity
     * @param peersManager      the peers manager
     * @param inboundValidator  the inbound validator
     * @param outboundValidator the outbound validator
     * @param handlers          the handlers
     */
    public EmbeddedPipeline(final DrasylConfig config,
                            final Identity identity,
                            final PeersManager peersManager,
                            final TypeValidator inboundValidator,
                            final TypeValidator outboundValidator,
                            final Handler... handlers) {
        this(config, identity, peersManager, inboundValidator, outboundValidator);
        List.of(handlers).forEach(handler -> addLast(handler.getClass().getSimpleName() + Crypto.randomString(8), handler));
    }

    public EmbeddedPipeline(final DrasylConfig config,
                            final Identity identity,
                            final PeersManager peersManager,
                            final TypeValidator inboundValidator,
                            final TypeValidator outboundValidator) {
        this.config = config;
        inboundMessages = ReplaySubject.create();
        inboundEvents = ReplaySubject.create();
        outboundMessages = ReplaySubject.create();

        this.handlerNames = new ConcurrentHashMap<>();
        this.head = new AbstractEndHandler(HeadContext.DRASYL_HEAD_HANDLER, config, this, DrasylScheduler.getInstanceHeavy(), identity, peersManager, inboundValidator, outboundValidator) {
            @Override
            public void write(final HandlerContext ctx,
                              final Address recipient,
                              final Object msg,
                              final CompletableFuture<Void> future) {
                    outboundMessages.onNext(Pair.of(recipient, msg));
                    future.complete(null);
            }
        };
        this.tail = new TailContext(inboundEvents::onNext, config, this, DrasylScheduler.getInstanceHeavy(), identity, peersManager, inboundValidator, outboundValidator) {
            @Override
            public void read(final HandlerContext ctx,
                             final Address sender,
                             final Object msg,
                             final CompletableFuture<Void> future) {
                if (sender instanceof CompressedPublicKey) {
                    final CompressedPublicKey senderAddress = (CompressedPublicKey) sender;
                    inboundEvents.onNext(new MessageEvent(senderAddress, msg));
                }
                inboundMessages.onNext(Pair.of(sender, msg));

                future.complete(null);
            }
        };
        this.scheduler = DrasylScheduler.getInstanceLight();
        this.identity = identity;
        this.peersManager = peersManager;
        this.inboundValidator = inboundValidator;
        this.outboundValidator = outboundValidator;

        initPointer();
    }

    /**
     * @return all messages that passes the pipeline until the end
     */
    public Observable<Pair<Address, Object>> inboundMessages() {
        return inboundMessages;
    }

    /**
     * @return all events that passes the pipeline until the end
     */
    public Observable<Event> inboundEvents() {
        return inboundEvents;
    }

    /**
     * @return all messages that passes the pipeline until the end
     */
    public <T> Observable<T> outboundOnlyMessages(final Class<T> clazz) {
        @SuppressWarnings("unchecked") final Observable<T> result = (Observable<T>) outboundMessages.map(Pair::second).filter(clazz::isInstance);
        return result;
    }

    /**
     * @return all messages that passes the pipeline until the end
     */
    public Observable<Object> outboundOnlyMessages() {
        return outboundMessages.map(Pair::second);
    }

    /**
     * @return all messages that passes the pipeline until the end
     */
    public Observable<Pair<Address, Object>> outboundMessages() {
        return outboundMessages;
    }

    /**
     * This method does release all potentially acquired {@link io.netty.util.ReferenceCounted}
     * objects.
     */
    @Override
    public void close() {
        for (final Pair o : ((ReplaySubject<Pair<Address, Object>>) outboundMessages).getValues(new Pair[]{})) {
            ReferenceCountUtil.safeRelease(o.second());
        }

        for (final Pair o : ((ReplaySubject<Pair<Address, Object>>) inboundMessages).getValues(new Pair[]{})) {
            ReferenceCountUtil.safeRelease(o.second());
        }
    }
}