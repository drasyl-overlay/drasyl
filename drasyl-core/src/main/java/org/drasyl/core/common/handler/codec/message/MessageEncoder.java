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

package org.drasyl.core.common.handler.codec.message;

import org.drasyl.core.common.messages.IMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.List;

/**
 * Encodes a {@link IMessage} into a {@link String} object.
 */
@Sharable
public class MessageEncoder extends MessageToMessageEncoder<IMessage> {
    private static final Logger LOG = LoggerFactory.getLogger(MessageEncoder.class);
    public static final MessageEncoder INSTANCE = new MessageEncoder();
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private MessageEncoder() {
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, IMessage msg, List<Object> out) {
        if (LOG.isDebugEnabled())
            LOG.debug("[{}]: Send message '{}'", ctx.channel().id().asShortText(), msg);

        try {
            String json = JSON_MAPPER.writeValueAsString(msg);

            out.add(new TextWebSocketFrame(json));
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Your request was not a valid Message Object: '" + msg + "'");
        }
    }
}