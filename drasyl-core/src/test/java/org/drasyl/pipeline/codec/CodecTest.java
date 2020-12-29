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
package org.drasyl.pipeline.codec;

import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CodecTest {
    @Mock
    private HandlerContext ctx;
    @Mock
    private CompressedPublicKey recipient;

    @Test
    void shouldSkipDoneFutures() {
        final Codec<Object, Object, Address> codec = new Codec<>() {
            @Override
            void encode(final HandlerContext ctx,
                        final Address recipient,
                        final Object msg,
                        final Consumer<Object> passOnConsumer) {
                passOnConsumer.accept(msg);
            }

            @Override
            void decode(final HandlerContext ctx,
                        final Address sender,
                        final Object msg,
                        final BiConsumer<Address, Object> passOnConsumer) {
                passOnConsumer.accept(sender, msg);
            }
        };

        final Object msg = mock(Object.class);
        final CompletableFuture<Void> future = CompletableFuture.completedFuture(null);

        codec.matchedWrite(ctx, recipient, msg, future);

        verify(ctx).write(recipient, msg, future);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldCompleteParentFutureExceptionallyOnChildError() {
        final Codec<Object, Object, Address> codec = new Codec<>() {
            @Override
            void encode(final HandlerContext ctx,
                        final Address recipient,
                        final Object msg,
                        final Consumer<Object> passOnConsumer) {
                passOnConsumer.accept(msg);
            }

            @Override
            void decode(final HandlerContext ctx,
                        final Address sender,
                        final Object msg,
                        final BiConsumer<Address, Object> passOnConsumer) {
                passOnConsumer.accept(sender, msg);
            }
        };

        final Object msg = mock(Object.class);
        final CompletableFuture<Void> future = new CompletableFuture<>();
        final ArgumentCaptor<CompletableFuture<Void>> captor = ArgumentCaptor.forClass(CompletableFuture.class);

        codec.matchedWrite(ctx, recipient, msg, future);

        verify(ctx).write(eq(recipient), eq(msg), captor.capture());
        captor.getValue().completeExceptionally(new Exception());
        assertThrows(Exception.class, future::join);
    }
}