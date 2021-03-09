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
package org.drasyl.pipeline.handler;

import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.HandlerAdapter;
import org.drasyl.util.TokenBucket;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.time.Duration;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.requireNonNull;

/**
 * Traffic shaping handler that limits the number of outgoing messages per second. For this purpose,
 * all outgoing messages are first consumed from the pipeline and queued in a FIFO queue. All queued
 * messages are then asynchronously dequeued with the given rate limit and finally re-added to the
 * pipeline.
 */
public class OutboundMessagesThrottlingHandler extends HandlerAdapter {
    @SuppressWarnings("unused")
    public static final String OUTBOUND_MESSAGES_THROTTLING_HANDLER = "OUTBOUND_MESSAGES_THROTTLING_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(OutboundMessagesThrottlingHandler.class);
    private final RateLimitedQueue queue;

    OutboundMessagesThrottlingHandler(final RateLimitedQueue queue) {
        this.queue = requireNonNull(queue);
    }

    public OutboundMessagesThrottlingHandler(final long maxEventsPerSecond) {
        this(new RateLimitedQueue(maxEventsPerSecond));
    }

    @Override
    public void write(final HandlerContext ctx,
                      final Address recipient,
                      final Object msg,
                      final CompletableFuture<Void> future) {
        queue.add(ctx, () -> ctx.write(recipient, msg, future));
    }

    public static class RateLimitedQueue {
        public final Queue<Runnable> queue;
        public final TokenBucket tokenBucket;
        private Disposable queueConsumer;

        public RateLimitedQueue(final long maxEventsPerSecond) {
            if (maxEventsPerSecond < 1) {
                throw new IllegalArgumentException("maxEventsPerSecond must be a positive number.");
            }
            queue = new LinkedList<>();
            final Duration refillInterval = Duration.ofSeconds(1).dividedBy(maxEventsPerSecond);
            final boolean doBusyWait = refillInterval.toMillis() < 20;
            tokenBucket = new TokenBucket(1, refillInterval, doBusyWait);
        }

        public synchronized void add(final HandlerContext ctx, final Runnable value) {
            queue.add(value);
            LOG.trace("New message has been enqueued. Messages in queue: {}", queue::size);
            if (queueConsumer == null) {
                this.queueConsumer = ctx.dependentScheduler().scheduleDirect(new QueueConsumer(this));
            }
        }

        public boolean tryConsume() {
            final Runnable runnable = queue.poll();
            if (runnable != null) {
                tokenBucket.consume();
                LOG.trace("Consume message. Messages in queue: {}", queue::size);
                runnable.run();
                return true;
            }
            else {
                return false;
            }
        }
    }

    public static class QueueConsumer implements Runnable {
        private final RateLimitedQueue queue;

        QueueConsumer(final RateLimitedQueue queue) {
            this.queue = requireNonNull(queue);
        }

        @Override
        public void run() {
            LOG.trace("Queue Consumer started.");
            for (; ; ) {
                if (!queue.tryConsume()) {
                    break;
                }
            }
            queue.queueConsumer = null;
            LOG.trace("Queue Consumer is done.");
        }
    }
}
