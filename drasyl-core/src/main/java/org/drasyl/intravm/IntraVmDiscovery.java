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
package org.drasyl.intravm;

import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.PeerInformation;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.message.ApplicationMessage;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Uses shared memory to discover other drasyl nodes running on same JVM.
 * <p>
 * Inspired by: https://github.com/actoron/jadex/blob/10e464b230d7695dfd9bf2b36f736f93d69ee314/platform/base/src/main/java/jadex/platform/service/awareness/IntraVMAwarenessAgent.java
 */
@SuppressWarnings({ "java:S110" })
public class IntraVmDiscovery extends SimpleDuplexHandler<ApplicationMessage, ApplicationMessage, CompressedPublicKey> {
    public static final IntraVmDiscovery INSTANCE = new IntraVmDiscovery();
    public static final String INTRA_VM_DISCOVERY = "INTRA_VM_DISCOVERY";
    private static final Logger LOG = LoggerFactory.getLogger(IntraVmDiscovery.class);
    private static final PeerInformation peerInformation = PeerInformation.of();
    private static final Object path = IntraVmDiscovery.class;
    private final Map<Pair<Integer, CompressedPublicKey>, HandlerContext> discoveries;
    private final ReadWriteLock lock;

    IntraVmDiscovery() {
        this(new HashMap<>(), new ReentrantReadWriteLock(true));
    }

    IntraVmDiscovery(final Map<Pair<Integer, CompressedPublicKey>, HandlerContext> discoveries,
                     final ReadWriteLock lock) {
        this.discoveries = discoveries;
        this.lock = lock;
    }

    @Override
    public void eventTriggered(final HandlerContext ctx,
                               final Event event,
                               final CompletableFuture<Void> future) {
        if (event instanceof NodeUpEvent) {
            startDiscovery(ctx);
        }
        else if (event instanceof NodeUnrecoverableErrorEvent || event instanceof NodeDownEvent) {
            stopDiscovery(ctx);
        }

        // passthrough event
        ctx.fireEventTriggered(event, future);
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final CompressedPublicKey sender,
                               final ApplicationMessage msg,
                               final CompletableFuture<Void> future) {
        final CompressedPublicKey recipient = msg.getRecipient();
        if (!ctx.identity().getPublicKey().equals(recipient)) {
            final HandlerContext discoveree = discoveries.get(Pair.of(ctx.config().getNetworkId(), recipient));

            if (discoveree == null) {
                // passthrough message
                ctx.fireRead(sender, msg, future);
            }
            else {
                discoveree.fireRead(sender, msg, future);
            }
        }
        else {
            // passthrough message
            ctx.fireRead(sender, msg, future);
        }
    }

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final CompressedPublicKey recipient,
                                final ApplicationMessage msg,
                                final CompletableFuture<Void> future) {
        final HandlerContext discoveree = discoveries.get(Pair.of(ctx.config().getNetworkId(), msg.getRecipient()));

        if (discoveree == null) {
            // passthrough message
            ctx.write(recipient, msg, future);
        }
        else {
            discoveree.fireRead(msg.getSender(), msg, future);
        }
    }

    private synchronized void startDiscovery(final HandlerContext myCtx) {
        try {
            lock.writeLock().lock();
            LOG.debug("Start Intra VM Discovery...");

            // store peer information
            discoveries.forEach((key, otherCtx) -> {
                final Integer networkId = key.first();
                final CompressedPublicKey publicKey = key.second();
                if (myCtx.config().getNetworkId() == networkId) {
                    otherCtx.peersManager().setPeerInformationAndAddPath(myCtx.identity().getPublicKey(), peerInformation, path);
                    myCtx.peersManager().setPeerInformationAndAddPath(publicKey, peerInformation, path);
                }
            });
            discoveries.put(
                    Pair.of(myCtx.config().getNetworkId(), myCtx.identity().getPublicKey()),
                    myCtx
            );

            LOG.debug("Intra VM Discovery started.");
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    private synchronized void stopDiscovery(final HandlerContext ctx) {
        try {
            lock.writeLock().lock();
            LOG.debug("Stop Intra VM Discovery...");

            // remove peer information
            discoveries.remove(Pair.of(ctx.config().getNetworkId(), ctx.identity().getPublicKey()));
            discoveries.forEach((key, otherCtx) -> {
                final Integer networkId = key.first();
                final CompressedPublicKey publicKey = key.second();
                if (ctx.config().getNetworkId() == networkId) {
                    otherCtx.peersManager().removePath(publicKey, path);
                    ctx.peersManager().removePath(publicKey, path);
                }
            });

            LOG.debug("Intra VM Discovery stopped.");
        }
        finally {
            lock.writeLock().unlock();
        }
    }
}