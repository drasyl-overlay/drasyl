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

import org.drasyl.util.TokenBucket.TokenProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static java.time.Duration.ZERO;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenBucketTest {
    @Nested
    class Constructor {
        @Test
        void shouldAcceptValidArguments() {
            assertThat(new TokenBucket(1, ofMillis(100), true), instanceOf(TokenBucket.class));
        }

        @Test
        void shouldRejectIllegalArguments(@Mock final TokenProvider tokenProvider) {
            final Duration oneSecond = ofSeconds(1);
            assertThrows(IllegalArgumentException.class, () -> new TokenBucket(0, oneSecond, false));
            assertThrows(IllegalArgumentException.class, () -> new TokenBucket(1, ZERO, false));
            assertThrows(IllegalArgumentException.class, () -> new TokenBucket(1, tokenProvider, () -> {
            }, 10));
        }
    }

    @Nested
    class Consume {
        @Test
        void shouldConsumeAvailableToken(@Mock final TokenProvider tokenProvider,
                                         @Mock final Runnable sleepStrategy) {
            final TokenBucket bucket = new TokenBucket(10, tokenProvider, sleepStrategy, 10);

            bucket.consume();

            assertEquals(9, bucket.availableTokens());
        }

        @Test
        void shouldGiveTokenProviderChanceToFillBucket(@Mock final TokenProvider tokenProvider,
                                                       @Mock final Runnable sleepStrategy) {
            when(tokenProvider.provide()).thenReturn(1L).thenReturn(0L);

            final TokenBucket bucket = new TokenBucket(10, tokenProvider, sleepStrategy, 5);

            bucket.consume();

            verify(tokenProvider).provide();
            assertEquals(5, bucket.availableTokens());
        }

        @Test
        void shouldApplyGivenSleepStrategy(@Mock final TokenProvider tokenProvider,
                                           @Mock final Runnable sleepStrategy) {
            when(tokenProvider.provide()).thenReturn(0L).thenReturn(1L);

            final TokenBucket bucket = new TokenBucket(1, tokenProvider, sleepStrategy, 0);

            bucket.consume();

            verify(sleepStrategy).run();
        }
    }

    @Nested
    class AvailableTokens {
        @Test
        void shouldReturnNumberOfAvailableTokens(@Mock final TokenProvider tokenProvider,
                                                 @Mock final Runnable sleepStrategy) {
            final TokenBucket bucket = new TokenBucket(10, tokenProvider, sleepStrategy, 10);

            assertEquals(10, bucket.availableTokens());
        }

        @Test
        void shouldGiveTokenProviderChanceToFillBucket(@Mock final TokenProvider tokenProvider,
                                                       @Mock final Runnable sleepStrategy) {
            when(tokenProvider.provide()).thenReturn(5L);

            final TokenBucket bucket = new TokenBucket(10, tokenProvider, sleepStrategy, 0);

            assertEquals(5, bucket.availableTokens());
        }
    }

    @Nested
    class TestTokenProvider {
        @Nested
        class Refill {
            @Test
            void shouldRefillProvideToken() {
                final TokenProvider provider = new TokenProvider(ofMillis(100));

                assertEquals(1, provider.provide());
                assertEquals(0, provider.provide());
                await().atMost(ofMillis(150)).untilAsserted(() -> assertThat(provider.provide(), greaterThan(0L)));
                assertEquals(0, provider.provide());
            }
        }
    }
}
