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
package org.drasyl.peer.connection;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelId;
import io.netty.util.Attribute;
import io.netty.util.concurrent.EventExecutor;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.connection.message.QuitMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_NEW_SESSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PeerChannelGroupTest {
    @Mock
    private Map<CompressedPublicKey, ChannelId> identity2channelId;
    @Mock
    private EventExecutor executor;
    @InjectMocks
    private PeerChannelGroup underTest;

    @Nested
    class WriteAndFlush {
        @Mock
        private CompressedPublicKey identity;
        @Mock
        private Object message;
        @Mock
        private Channel channel;
        @Mock
        private ChannelId channelId;
        @Mock
        private Attribute attribute;
        @Mock
        private ChannelFuture channelFuture;

        @Test
        void itShouldWriteToChannelIfExists() {
            when(channel.id()).thenReturn(channelId);
            when(channel.attr(any())).thenReturn(attribute);
            when(channel.closeFuture()).thenReturn(channelFuture);
            when(identity2channelId.get(identity)).thenReturn(channelId);
            underTest.add(identity, channel);

            underTest.writeAndFlush(identity, message);

            verify(channel).writeAndFlush(message);
        }

        @Test
        void itShouldReturnFailedFutureIfChannelDoesNotExists() throws ExecutionException, InterruptedException {
            underTest.writeAndFlush(identity, message);

            verify(executor).newFailedFuture(any());
        }
    }

    @Nested
    class Find {
        @Mock
        private CompressedPublicKey identity;
        @Mock
        private Channel channel;
        @Mock
        private ChannelId channelId;
        @Mock
        private Attribute attribute;
        @Mock
        private ChannelFuture channelFuture;

        @Test
        void itShouldFindChannelWithGivenIdentity() {
            when(channel.id()).thenReturn(channelId);
            when(channel.attr(any())).thenReturn(attribute);
            when(channel.closeFuture()).thenReturn(channelFuture);
            when(identity2channelId.get(identity)).thenReturn(channelId);
            underTest.add(identity, channel);

            assertEquals(channel, underTest.find(identity));
        }
    }

    @Nested
    class Add {
        @Mock
        private Channel channel;
        @Mock
        private CompressedPublicKey identity;
        @Mock
        private ChannelId channelId;
        @Mock
        private Attribute attribute;
        @Mock
        private ChannelFuture channelFuture;

        @Test
        void itShouldAddGivenChannelToGroup() {
            when(channel.attr(any())).thenReturn(attribute);
            when(attribute.get()).thenReturn(identity);
            when(channel.id()).thenReturn(channelId);
            when(channel.closeFuture()).thenReturn(channelFuture);

            underTest.add(channel);

            verify(identity2channelId).put(identity, channelId);
        }
    }

    @Nested
    class AddWithIdentity {
        @Mock
        private Channel channel;
        @Mock
        private CompressedPublicKey identity;
        @Mock
        private ChannelId channelId;
        @Mock
        private Attribute attribute;
        @Mock
        private ChannelFuture channelFuture;
        @Mock
        private Channel existingChannel;
        @Mock
        private Attribute existingChannelAttribute;
        @Mock
        private ChannelId existingChannelId;

        @BeforeEach
        void setUp() {
            when(channel.attr(any())).thenReturn(attribute);
            when(channel.id()).thenReturn(channelId);
            when(channel.closeFuture()).thenReturn(channelFuture);
            when(existingChannel.attr(any())).thenReturn(existingChannelAttribute);
            when(existingChannel.id()).thenReturn(existingChannelId);
            when(existingChannel.closeFuture()).thenReturn(channelFuture);
            when(existingChannel.writeAndFlush(any())).thenReturn(channelFuture);
            when(identity2channelId.get(identity)).thenReturn(existingChannelId);

            underTest.add(identity, existingChannel);
        }

        @Test
        void itShouldAddGivenChannelToGroup() {
            assertTrue(underTest.add(identity, channel));

            verify(identity2channelId).put(identity, channelId);
        }

        @Test
        void itShouldCloseExistingChannelsWithEqualIdentity() {
            underTest.add(identity, channel);

            verify(existingChannel).writeAndFlush(new QuitMessage(REASON_NEW_SESSION));
            verify(channelFuture).addListener(ChannelFutureListener.CLOSE);
        }

        @Test
        void itShouldSetIdentityAttribute() {
            underTest.add(identity, channel);

            verify(attribute).set(identity);
        }
    }

    @Nested
    class Remove {
        @Mock
        private Channel channel;
        @Mock
        private CompressedPublicKey identity;
        @Mock
        private ChannelId channelId;
        @Mock
        private Attribute attribute;
        @Mock
        private ChannelFuture channelFuture;

        @BeforeEach
        void setUp() {
            when(channel.attr(any())).thenReturn(attribute);
            when(channel.id()).thenReturn(channelId);
            when(channel.closeFuture()).thenReturn(channelFuture);
            when(attribute.get()).thenReturn(identity);

            underTest.add(identity, channel);
        }

        @Test
        void itShouldRemoveGivenChannelFromGroup() {
            assertTrue(underTest.remove(channel));

            verify(identity2channelId).remove(identity);
        }
    }
}