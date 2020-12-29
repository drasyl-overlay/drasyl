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

import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.drasyl.util.FutureUtil;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Standard interface for all codecs of the {@link org.drasyl.pipeline.Pipeline}.
 * <br>
 * A codec can be used to encode/decode a given set of objects into the correct format to process
 * the object in the ongoing steps.
 * <br>
 * A codec must have a symmetrical construction. {@link #encode(HandlerContext, Address, Object,
 * Consumer)} converts an object of type D into type E and {@link #decode(HandlerContext, Address,
 * Object, BiConsumer)} vice versa.
 *
 * <p>
 * <b>Note</b>: You can use the {@link HandlerContext#inboundValidator()}} or {@link
 * HandlerContext#outboundValidator()}} to check if a given {@code Class} is allowed to be
 * encode/decode.
 * </p>
 */
@SuppressWarnings({ "java:S110" })
public abstract class Codec<E, D, A extends Address> extends SimpleDuplexHandler<E, D, A> {
    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final A sender,
                               final E msg,
                               final CompletableFuture<Void> future) {
        if (future.isDone()) {
            ctx.fireRead(sender, msg, future);
            return;
        }

        final ArrayList<CompletableFuture<?>> futures = new ArrayList<>();

        decode(ctx, sender, msg, (decodedSender, decodedMessage) -> {
            final CompletableFuture<Void> dependingFuture = new CompletableFuture<>();
            futures.add(dependingFuture);

            ctx.fireRead(decodedSender, decodedMessage, dependingFuture);
        });

        FutureUtil.completeOnAllOf(future, futures);
    }

    /**
     * Decodes a given object of type {@code E} into type {@code D}.
     * <p>
     * You have to use the given {@code passOnConsumer} to pass all objects to the next handler in
     * the pipeline, no matter whether they have been decoded or not.
     * <p>
     * A codec should never act as a guard, but rather pass on all messages that it could not
     * handle. There is always the possibility that there is another codec in the pipeline that can
     * handle this object.
     *
     * @param ctx            the handler context
     * @param sender         the sender
     * @param msg            the message that should be decoded
     * @param passOnConsumer to pass messages to the next handler in the pipeline
     */
    abstract void decode(HandlerContext ctx,
                         A sender,
                         E msg,
                         BiConsumer<Address, Object> passOnConsumer);

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final A recipient,
                                final D msg,
                                final CompletableFuture<Void> future) {
        if (future.isDone()) {
            ctx.write(recipient, msg, future);
            return;
        }

        final ArrayList<CompletableFuture<?>> futures = new ArrayList<>();

        encode(ctx, recipient, msg, encodedMessage -> {
            final CompletableFuture<Void> dependingFuture = new CompletableFuture<>();
            futures.add(dependingFuture);

            ctx.write(recipient, encodedMessage, dependingFuture);
        });

        FutureUtil.completeOnAllOf(future, futures);
    }

    /**
     * Encodes a given object of type {@code D} into type {@code E}.
     *
     * <br>
     * You have to use the given {@code passOnConsumer} to pass all objects to the next handler in
     * the pipeline, no matter whether they have been encoded or not.
     * <p>
     * A codec should never act as a guard, but rather pass on all messages that it could not
     * handle. There is always the possibility that there is another codec in the pipeline that can
     * handle this object.
     *
     * @param ctx            the handler context
     * @param recipient      the recipient
     * @param msg            the message that should be encoded
     * @param passOnConsumer to pass messages to the next handler in the pipeline
     */
    abstract void encode(HandlerContext ctx,
                         A recipient,
                         D msg,
                         Consumer<Object> passOnConsumer);
}