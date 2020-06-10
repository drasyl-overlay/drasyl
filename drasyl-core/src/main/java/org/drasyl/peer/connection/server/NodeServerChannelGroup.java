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
package org.drasyl.peer.connection.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelId;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.QuitMessage;

import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_NEW_SESSION;

/**
 * Special type of {@link ChannelGroup}, which has a lookup complexity of O(1) instead of O(n) for
 * lookups by {@link Identity}.
 */
public class NodeServerChannelGroup extends DefaultChannelGroup {
    public static final AttributeKey<Identity> ATTRIBUTE_IDENTITY = AttributeKey.valueOf("identity");
    private final Map<Identity, ChannelId> identity2channelId;
    private final ChannelFutureListener remover = future -> remove(future.channel());

    public NodeServerChannelGroup() {
        this(new HashMap<>(), GlobalEventExecutor.INSTANCE);
    }

    NodeServerChannelGroup(Map<Identity, ChannelId> identity2channelId, EventExecutor executor) {
        super(executor);
        this.identity2channelId = identity2channelId;
    }

    public NodeServerChannelGroup(EventExecutor executor) {
        this(new HashMap<>(), executor);
    }

    public ChannelFuture writeAndFlush(Identity identity, Object message) {
        Channel existingChannel = find(identity);
        if (existingChannel != null) {
            return existingChannel.writeAndFlush(message);
        }
        else {
            throw new IllegalArgumentException("No channel with given Identity found.");
        }
    }

    public Channel find(Identity identity) {
        ChannelId existingChannelId = identity2channelId.get(identity);
        if (existingChannelId != null) {
            return find(existingChannelId);
        }
        else {
            return null;
        }
    }

    @Override
    public boolean add(Channel channel) {
        Identity identity = channel.attr(ATTRIBUTE_IDENTITY).get();
        return add(identity, channel);
    }

    @Override
    public boolean remove(Object o) {
        if (o instanceof Channel) {
            Channel channel = (Channel) o;
            Identity identity = channel.attr(ATTRIBUTE_IDENTITY).get();
            identity2channelId.remove(identity);
        }
        else if (o instanceof ChannelId) {
            identity2channelId.values().remove(o);
        }

        return super.remove(o);
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    public boolean add(Identity identity, Channel channel) {
        requireNonNull(identity);

        // close any existing connections with the same peer...
        Channel existingChannel = find(identity);
        if (existingChannel != null) {
            existingChannel.writeAndFlush(new QuitMessage(REASON_NEW_SESSION)).addListener(ChannelFutureListener.CLOSE);
        }

        // ...before adding the new one
        channel.attr(ATTRIBUTE_IDENTITY).set(identity);
        boolean added = super.add(channel);
        identity2channelId.put(identity, channel.id());

        if (added) {
            channel.closeFuture().addListener(remover);
        }

        return added;
    }
}