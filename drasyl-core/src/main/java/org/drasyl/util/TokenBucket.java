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
package org.drasyl.util;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Uninterruptibles;

import java.time.Duration;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * This class implements the <a href="https://en.wikipedia.org/wiki/Token_bucket">token bucket
 * algorithm</a> as a leaky bucket. This means that the bucket has a finite capacity and added
 * tokens that exceed this capacity are lost. The bucket is filled with new tokens at a constant
 * time rate.
 * <p>
 * This algorithm allows rate-limiting operations (e.g. number of messages sent per time unit). The
 * bucket capacity can be used to control whether capacities unused in the past may be used up
 * later, or whether they "overflow" from the bucket and are lost unused.
 * <p>
 * This implementation has been inspired by: <a href="https://github.com/bbeck/token-bucket/tree/master">token-bucket</>
 */
public class TokenBucket {
    private final long capacity;
    private final TokenProvider tokenProvider;
    private final Runnable sleepStrategy;
    private long tokens;

    TokenBucket(final long capacity,
                final TokenProvider tokenProvider,
                final Runnable sleepStrategy,
                final long tokens) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be a positive number.");
        }
        if (tokens > capacity) {
            throw new IllegalArgumentException("tokens must not be greater than capacity.");
        }
        this.capacity = capacity;
        this.tokenProvider = requireNonNull(tokenProvider);
        this.sleepStrategy = requireNonNull(sleepStrategy);
        this.tokens = tokens;
    }

    /**
     * Creates a new leaky token bucket.
     *
     * @param capacity       overall capacity of the token bucket
     * @param refillInterval refill token at a fixed interval
     * @param doBusyWait     specifies if busy waiting should be used when calling {@link
     *                       #consume()} or not. busy waiting allows a more efficient consumption of
     *                       tokens, but blocks a thread permanently. should only be used with
     *                       refill intervals of less than 20 milliseconds.
     * @throws IllegalArgumentException if {@code capacity} or {@code refillInterval} contain
     *                                  non-positive values
     */
    public TokenBucket(final long capacity,
                       final Duration refillInterval,
                       final boolean doBusyWait) {
        this(capacity, new TokenProvider(refillInterval), () -> {
            if (!doBusyWait) {
                // sleep for the smallest possible unit of time to relinquish control and allow other threads to run.
                Uninterruptibles.sleepUninterruptibly(1, NANOSECONDS);
            }
        }, 0);
    }

    /**
     * Consumes a token from the bucket. If the bucket does not currently contain a token, this
     * method blocks until a token becomes available.
     */
    @SuppressWarnings("ConditionalBreakInInfiniteLoop")
    public void consume() {
        while (true) {
            if (tryConsume()) {
                break;
            }

            sleepStrategy.run();
        }
    }

    /**
     * Returns the number of available tokens in the bucket.
     *
     * @return numebr of available tokens in the bucket
     */
    public synchronized long availableTokens() {
        // give token provider chance to fill up bucket
        conditionalRefill();

        return tokens;
    }

    private synchronized boolean tryConsume() {
        // give token provider chance to fill up bucket
        conditionalRefill();

        if (tokens > 0) {
            tokens -= 1;
            return true;
        }
        else {
            return false;
        }
    }

    private void conditionalRefill() {
        final long newTokens = tokenProvider.provide();
        if (newTokens > 0) {
            tokens = Math.max(tokens, Math.min(tokens + newTokens, capacity));
        }
    }

    static class TokenProvider {
        private final Stopwatch stopwatch;
        private final long refillInterval;
        private long lastRefillTime;

        TokenProvider(final long refillInterval,
                      final Stopwatch stopwatch,
                      final long lastRefillTime) {
            if (refillInterval < 1) {
                throw new IllegalArgumentException("refillIntervalDuration must be positive duration.");
            }
            this.stopwatch = stopwatch;
            this.refillInterval = refillInterval;
            this.lastRefillTime = lastRefillTime;
        }

        TokenProvider(final Duration refillIntervalDuration) {
            this(refillIntervalDuration.toNanos(), Stopwatch.createStarted(), -refillIntervalDuration.toNanos());
        }

        public synchronized long provide() {
            final long elapsedTime = stopwatch.elapsed(NANOSECONDS);
            if (elapsedTime < lastRefillTime + refillInterval) {
                return 0;
            }

            // bucket need to be refilled -> add new token(s)
            final long newTokens = (elapsedTime - lastRefillTime) / refillInterval;
            lastRefillTime += newTokens * refillInterval;
            return newTokens;
        }
    }
}
