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
import org.drasyl.pipeline.Stateless;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.message.ApplicationMessage;
import org.drasyl.util.JSONUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * This default codec allows to encode/decode all supported objects by Jackson.
 */
@Stateless
@SuppressWarnings({ "java:S110" })
public class DefaultCodec extends Codec<ApplicationMessage, Object, Address> {
    public static final DefaultCodec INSTANCE = new DefaultCodec();
    public static final String DEFAULT_CODEC = "defaultCodec";
    private static final Logger LOG = LoggerFactory.getLogger(DefaultCodec.class);

    private DefaultCodec() {
    }

    @Override
    void encode(final HandlerContext ctx,
                final Address recipient,
                final Object msg,
                final Consumer<Object> passOnConsumer) {
        if (recipient instanceof CompressedPublicKey && msg instanceof byte[]) {
            // skip byte arrays
            passOnConsumer.accept(new ApplicationMessage(ctx.identity().getPublicKey(), (CompressedPublicKey) recipient, byte[].class, (byte[]) msg));

            LOG.trace("[{}]: Encoded Message '{}'", ctx::name, () -> msg);
        }
        else if (recipient instanceof CompressedPublicKey && ctx.outboundValidator().validate(msg.getClass()) && JSONUtil.JACKSON_WRITER.canSerialize(msg.getClass())) {
            final ByteBuf buf = PooledByteBufAllocator.DEFAULT.buffer();
            try (final ByteBufOutputStream bos = new ByteBufOutputStream(buf)) {

                JSONUtil.JACKSON_WRITER.writeValue((OutputStream) bos, msg);

                final byte[] b = new byte[buf.readableBytes()];
                buf.getBytes(buf.readerIndex(), b);

                passOnConsumer.accept(new ApplicationMessage(ctx.identity().getPublicKey(), (CompressedPublicKey) recipient, msg.getClass(), b));

                LOG.trace("[{}]: Encoded Message '{}'", ctx::name, () -> msg);
            }
            catch (final IOException e) {
                LOG.warn("[{}]: Unable to serialize '{}': ", ctx::name, () -> msg, () -> e);
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
                final Address sender,
                final ApplicationMessage msg,
                final BiConsumer<Address, Object> passOnConsumer) {
        try {
            if (byte[].class == msg.getTypeClazz()) {
                // skip byte arrays
                passOnConsumer.accept(msg.getSender(), msg.getContent());

                LOG.trace("[{}]: Decoded Message '{}'", ctx::name, msg::getContent);
            }
            else if (ctx.inboundValidator().validate(msg.getTypeClazz()) && JSONUtil.JACKSON_WRITER.canSerialize(msg.getTypeClazz())) {
                decodeObjectHolder(ctx, msg, passOnConsumer);
            }
            else {
                // can't decode, pass message to the next handler in the pipeline
                passOnConsumer.accept(sender, msg);
            }
        }
        catch (final ClassNotFoundException e) {
            LOG.warn("[{}]: Unable to deserialize '{}': ", ctx::name, () -> msg, () -> e);
            // can't decode, pass message to the next handler in the pipeline
            passOnConsumer.accept(sender, msg);
        }
    }

    void decodeObjectHolder(final HandlerContext ctx,
                            final ApplicationMessage msg,
                            final BiConsumer<Address, Object> passOnConsumer) throws ClassNotFoundException {
        final ByteBuf buf = Unpooled.wrappedBuffer(msg.getContent());
        try (final ByteBufInputStream bis = new ByteBufInputStream(buf)) {
            final Object decodedMessage = requireNonNull(JSONUtil.JACKSON_READER.readValue((InputStream) bis, msg.getTypeClazz()));
            passOnConsumer.accept(msg.getSender(), decodedMessage);

            LOG.trace("[{}]: Decoded Message '{}'", ctx::name, () -> decodedMessage);
        }
        catch (final IOException | IllegalArgumentException e) {
            LOG.warn("[{}]: Unable to deserialize '{}': ", ctx::name, () -> msg, () -> e);
            passOnConsumer.accept(msg.getSender(), msg);
        }
        finally {
            buf.release();
        }
    }
}
