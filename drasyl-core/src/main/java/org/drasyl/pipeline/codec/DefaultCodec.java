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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.util.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * This default codec allows to encode/decode all supported objects by Jackson.
 */
@SuppressWarnings({ "java:S110" })
public class DefaultCodec extends Codec<ObjectHolder, Object, CompressedPublicKey> {
    public static final DefaultCodec INSTANCE = new DefaultCodec();
    public static final String DEFAULT_CODEC = "defaultCodec";
    private static final Logger LOG = LoggerFactory.getLogger(DefaultCodec.class);

    private DefaultCodec() {
    }

    @Override
    void encode(final HandlerContext ctx,
                final Object msg,
                final Consumer<Object> passOnConsumer) {
        if (msg instanceof byte[]) {
            // skip byte arrays
            passOnConsumer.accept(ObjectHolder.of(byte[].class, (byte[]) msg));

            if (LOG.isTraceEnabled()) {
                LOG.trace("[{}]: Encoded Message '{}'", ctx.name(), msg);
            }
        }
        else if (ctx.outboundValidator().validate(msg.getClass()) && JSONUtil.JACKSON_WRITER.canSerialize(msg.getClass())) {
            final ByteBuf buf = PooledByteBufAllocator.DEFAULT.buffer();
            try (final ByteBufOutputStream bos = new ByteBufOutputStream(buf)) {

                JSONUtil.JACKSON_WRITER.writeValue((OutputStream) bos, msg);

                final byte[] b = new byte[buf.readableBytes()];
                buf.getBytes(buf.readerIndex(), b);

                passOnConsumer.accept(ObjectHolder.of(msg.getClass(), b));

                if (LOG.isTraceEnabled()) {
                    LOG.trace("[{}]: Encoded Message '{}'", ctx.name(), msg);
                }
            }
            catch (final IOException e) {
                LOG.warn("[{}]: Unable to serialize '{}': ", ctx.name(), msg, e);
                passOnConsumer.accept(msg);
            }
            finally {
                buf.release();
            }
        }
        else {
            // can't encode, pass message to the next handler in the pipeline
            passOnConsumer.accept(msg);
        }
    }

    @Override
    void decode(final HandlerContext ctx,
                final ObjectHolder msg,
                final Consumer<Object> passOnConsumer) {
        try {
            if (byte[].class == msg.getClazz()) {
                // skip byte arrays
                passOnConsumer.accept(msg.getObject());

                if (LOG.isTraceEnabled()) {
                    LOG.trace("[{}]: Decoded Message '{}'", ctx.name(), msg.getObject());
                }
            }
            else if (ctx.inboundValidator().validate(msg.getClazz()) && JSONUtil.JACKSON_WRITER.canSerialize(msg.getClazz())) {
                decodeObjectHolder(ctx, msg, passOnConsumer);
            }
            else {
                // can't decode, pass message to the next handler in the pipeline
                passOnConsumer.accept(msg);
            }
        }
        catch (final ClassNotFoundException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("[{}]: Unable to deserialize '{}': ", ctx.name(), msg, e);
            }
            // can't decode, pass message to the next handler in the pipeline
            passOnConsumer.accept(msg);
        }
    }

    void decodeObjectHolder(final HandlerContext ctx,
                            final ObjectHolder msg,
                            final Consumer<Object> passOnConsumer) throws ClassNotFoundException {
        final ByteBuf buf = Unpooled.wrappedBuffer(msg.getObject());
        try (final ByteBufInputStream bis = new ByteBufInputStream(buf)) {
            final Object decodedMessage = requireNonNull(JSONUtil.JACKSON_READER.readValue((InputStream) bis, msg.getClazz()));
            passOnConsumer.accept(decodedMessage);

            if (LOG.isTraceEnabled()) {
                LOG.trace("[{}]: Decoded Message '{}'", ctx.name(), decodedMessage);
            }
        }
        catch (final IOException | IllegalArgumentException e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("[{}]: Unable to deserialize '{}': ", ctx.name(), msg, e);
            }
            passOnConsumer.accept(msg);
        }
        finally {
            buf.release();
        }
    }
}
