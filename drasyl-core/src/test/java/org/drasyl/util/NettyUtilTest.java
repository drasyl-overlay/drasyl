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

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class NettyUtilTest {
    @Nested
    class GetBestEventLoopGroup {
        @SuppressWarnings("ConstantConditions")
        @Test
        void shouldReturnCorrectGroup() {
            EventLoopGroup group = null;
            try {
                group = NettyUtil.getBestEventLoopGroup(1);

                if (Epoll.isAvailable()) {
                    assertThat(group, instanceOf(EpollEventLoopGroup.class));
                }
                else if (KQueue.isAvailable()) {
                    assertThat(group, instanceOf(KQueueEventLoopGroup.class));
                }
                else {
                    assertThat(group, instanceOf(NioEventLoopGroup.class));
                }
            }
            finally {
                group.shutdownGracefully().awaitUninterruptibly();
            }
        }
    }

    @Nested
    class GetBestDatagramChannel {
        @Test
        void shouldReturnCorrectChannel() {
            final Class<? extends DatagramChannel> channel = NettyUtil.getBestDatagramChannel();

            if (Epoll.isAvailable()) {
                assertEquals(EpollDatagramChannel.class, channel);
            }
            else if (KQueue.isAvailable()) {
                assertEquals(KQueueDatagramChannel.class, channel);
            }
            else {
                assertEquals(NioDatagramChannel.class, channel);
            }
        }
    }
}
