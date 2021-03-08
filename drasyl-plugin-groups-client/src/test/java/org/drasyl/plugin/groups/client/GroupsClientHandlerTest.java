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
package org.drasyl.plugin.groups.client;

import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.serialization.Serialization;
import org.drasyl.plugin.groups.client.event.GroupJoinFailedEvent;
import org.drasyl.plugin.groups.client.event.GroupJoinedEvent;
import org.drasyl.plugin.groups.client.event.GroupLeftEvent;
import org.drasyl.plugin.groups.client.event.GroupMemberJoinedEvent;
import org.drasyl.plugin.groups.client.event.GroupMemberLeftEvent;
import org.drasyl.plugin.groups.client.message.GroupJoinFailedMessage;
import org.drasyl.plugin.groups.client.message.GroupJoinMessage;
import org.drasyl.plugin.groups.client.message.GroupLeaveMessage;
import org.drasyl.plugin.groups.client.message.GroupWelcomeMessage;
import org.drasyl.plugin.groups.client.message.GroupsClientMessage;
import org.drasyl.plugin.groups.client.message.GroupsServerMessage;
import org.drasyl.plugin.groups.client.message.MemberJoinedMessage;
import org.drasyl.plugin.groups.client.message.MemberLeftMessage;
import org.drasyl.serialization.JacksonJsonSerializer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupsClientHandlerTest {
    @Mock
    private HandlerContext ctx;
    @Mock
    private HashMap<Group, Disposable> renewTasks;
    @Mock
    private Map<Group, GroupUri> groups;
    @Mock
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock
    private ProofOfWork proofOfWork;
    @Mock
    private Group group;
    @Mock
    private GroupUri uri;
    @Mock
    private CompressedPublicKey publicKey;
    private final Duration firstStartDelay = Duration.ofMillis(1);

    @Nested
    class HandlerAdded {
        @Test
        void shouldStartHandler(@Mock final Serialization inboundSerialization,
                                @Mock final Serialization outboundSerialization) {
            when(ctx.inboundSerialization()).thenReturn(inboundSerialization);
            when(ctx.outboundSerialization()).thenReturn(outboundSerialization);

            final GroupsClientHandler handler = new GroupsClientHandler(Set.of());

            handler.handlerAdded(ctx);

            verify(inboundSerialization).addSerializer(eq(GroupsServerMessage.class), any(JacksonJsonSerializer.class));
            verify(outboundSerialization).addSerializer(eq(GroupsClientMessage.class), any(JacksonJsonSerializer.class));
        }
    }

    @Nested
    class HandlerRemoved {
        @Test
        void shouldStopRenewTasks(@Mock final Disposable disposable) {
            final Map<Group, Disposable> renewTasks = new HashMap<>(Map.of(group, disposable));
            final GroupsClientHandler handler = new GroupsClientHandler(groups, renewTasks, firstStartDelay);

            handler.handlerRemoved(ctx);

            verify(disposable).dispose();
            assertTrue(renewTasks.isEmpty());
        }

        @Test
        void shouldDeregisterFromGroups() {
            final Map<Group, GroupUri> groups = Map.of(group, uri);
            final GroupsClientHandler handler = new GroupsClientHandler(groups, renewTasks, firstStartDelay);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager)) {
                final TestObserver<GroupLeaveMessage> testObserver = pipeline.outboundMessages(GroupLeaveMessage.class).test();

                pipeline.addLast("handler", handler);
                pipeline.remove("handler");

                testObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new GroupLeaveMessage(group));

                verify(renewTasks).clear();
            }
        }
    }

    @Nested
    class OnEvent {
        @Test
        void shouldPassThroughOnNotMatchingEvent(@Mock final NodeOfflineEvent event) {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<Event> testObserver = pipeline.inboundEvents().test();

                pipeline.processInbound(event);

                testObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(event);
            }
        }

        @Test
        void shouldSendJoinOnNodeUpEvent(@Mock final NodeUpEvent event) {
            final String credentials = "test";
            final Map<Group, GroupUri> groups = Map.of(group, uri);
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<Event> eventObserver = pipeline.inboundEvents().test();
                final TestObserver<GroupJoinMessage> outboundObserver = pipeline.outboundMessages(GroupJoinMessage.class).test();

                when(uri.getGroup()).thenReturn(group);
                when(uri.getCredentials()).thenReturn(credentials);
                when(identity.getProofOfWork()).thenReturn(proofOfWork);

                pipeline.processInbound(event).join();

                eventObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(event);

                outboundObserver.awaitCount(1).assertValueCount(1);
                outboundObserver.assertValue(new GroupJoinMessage(uri.getGroup(), uri.getCredentials(), proofOfWork, false));
            }
        }
    }

    @Nested
    class Read {
        @Test
        void shouldProcessMemberJoined() {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<Event> eventObserver = pipeline.inboundEvents().test();
                final MemberJoinedMessage msg = new MemberJoinedMessage(publicKey, group);

                final CompletableFuture<Void> future = pipeline.processInbound(publicKey, msg);

                future.join();
                eventObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new GroupMemberJoinedEvent(publicKey, group));
                assertTrue(future.isDone());
            }
        }

        @Test
        void shouldProcessMemberLeft() {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<Event> eventObserver = pipeline.inboundEvents().test();
                final MemberLeftMessage msg = new MemberLeftMessage(publicKey, group);

                final CompletableFuture<Void> future = pipeline.processInbound(publicKey, msg);

                future.join();
                eventObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new GroupMemberLeftEvent(publicKey, group));
                assertTrue(future.isDone());
            }
        }

        @Test
        void shouldProcessOwnLeft() {
            when(identity.getPublicKey()).thenReturn(publicKey);

            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<Event> eventObserver = pipeline.inboundEvents().test();
                final MemberLeftMessage msg = new MemberLeftMessage(identity.getPublicKey(), group);

                final CompletableFuture<Void> future = pipeline.processInbound(publicKey, msg);

                future.join();
                eventObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new GroupLeftEvent(group, () -> {
                        }));
                assertTrue(future.isDone());
            }
        }

        @Test
        void shouldProcessWelcome() {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<Event> eventObserver = pipeline.inboundEvents().test();
                final GroupWelcomeMessage msg = new GroupWelcomeMessage(group, Set.of(publicKey));

                when(groups.get(any())).thenReturn(uri);
                when(uri.getTimeout()).thenReturn(Duration.ofMinutes(10));

                final CompletableFuture<Void> future = pipeline.processInbound(publicKey, msg);

                future.join();
                eventObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new GroupJoinedEvent(group, Set.of(publicKey), () -> {
                        }));
                assertTrue(future.isDone());
            }
        }

        @Test
        void shouldProcessJoinFailed() {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new HashMap<>(), firstStartDelay);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<Event> eventObserver = pipeline.inboundEvents().test();
                final GroupJoinFailedMessage.Error error = GroupJoinFailedMessage.Error.ERROR_GROUP_NOT_FOUND;
                final GroupJoinFailedMessage msg = new GroupJoinFailedMessage(group, error);

                final CompletableFuture<Void> future = pipeline.processInbound(publicKey, msg);

                future.join();
                eventObserver.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new GroupJoinFailedEvent(group, error, () -> {
                        }));

                assertTrue(future.isDone());
            }
        }
    }
}
