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
package org.drasyl.plugin.groups.client;

import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.plugin.groups.client.event.GroupJoinFailedEvent;
import org.drasyl.plugin.groups.client.event.GroupJoinedEvent;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupsClientHandlerTest {
    @Mock
    private HandlerContext ctx;
    @Mock
    private TypeValidator inboundValidator;
    @Mock
    private TypeValidator outboundValidator;
    @Mock
    private List<Disposable> renewTasks;
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

    @Nested
    class HandlerAdded {
        @Test
        void shouldAddClassesToValidator() {
            when(ctx.inboundValidator()).thenReturn(inboundValidator);
            when(ctx.outboundValidator()).thenReturn(outboundValidator);

            final GroupsClientHandler handler = new GroupsClientHandler(Set.of());

            handler.handlerAdded(ctx);

            verify(inboundValidator).addClass(eq(GroupsServerMessage.class));
            verify(outboundValidator).addClass(eq(GroupsClientMessage.class));
        }
    }

    @Nested
    class HandlerRemoved {
        @Test
        void shouldStopRenewTasks() {
            final Disposable disposable = mock(Disposable.class);
            final ArrayList<Disposable> renewTasks = new ArrayList<>(List.of(disposable));
            final GroupsClientHandler handler = new GroupsClientHandler(groups, renewTasks);

            handler.handlerRemoved(ctx);

            verify(disposable).dispose();
            assertTrue(renewTasks.isEmpty());
        }

        @Test
        void shouldDeregisterFromGroups() {
            final Map<Group, GroupUri> groups = Map.of(group, uri);
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new ArrayList<>());
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                    config,
                    identity,
                    peersManager,
                    inboundValidator,
                    outboundValidator);
            final TestObserver<GroupLeaveMessage> testObserver = pipeline.outboundOnlyMessages(GroupLeaveMessage.class).test();

            pipeline.addLast("handler", handler);
            pipeline.remove("handler");

            testObserver.awaitCount(1).assertValueCount(1);
            testObserver.assertValue(new GroupLeaveMessage(group));
            pipeline.close();
        }
    }

    @Nested
    class OnEvent {
        @Test
        void shouldPassThroughOnNotMatchingEvent() {
            final Event event = mock(NodeOfflineEvent.class);
            final GroupsClientHandler handler = new GroupsClientHandler(groups, renewTasks);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                    config,
                    identity,
                    peersManager,
                    inboundValidator,
                    outboundValidator,
                    handler);
            final TestObserver<Event> testObserver = pipeline.inboundEvents().test();

            pipeline.processInbound(event);

            testObserver.awaitCount(1).assertValueCount(1);
            testObserver.assertValue(event);
            pipeline.close();
        }

        @Test
        void shouldSendJoinOnNodeOnlineEvent() {
            final Event event = mock(NodeOnlineEvent.class);
            final String credentials = "test";
            final Map<Group, GroupUri> groups = Map.of(group, uri);
            final GroupsClientHandler handler = new GroupsClientHandler(groups, new ArrayList<>());
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                    config,
                    identity,
                    peersManager,
                    inboundValidator,
                    outboundValidator,
                    handler);
            final TestObserver<Event> eventObserver = pipeline.inboundEvents().test();
            final TestObserver<GroupJoinMessage> outboundObserver = pipeline.outboundOnlyMessages(GroupJoinMessage.class).test();

            when(uri.getGroup()).thenReturn(group);
            when(uri.getCredentials()).thenReturn(credentials);
            when(identity.getProofOfWork()).thenReturn(proofOfWork);

            pipeline.processInbound(event);

            outboundObserver.awaitCount(1).assertValueCount(1);
            outboundObserver.assertValue(new GroupJoinMessage(uri.getGroup(), uri.getCredentials(), proofOfWork));

            eventObserver.awaitCount(1).assertValueCount(1);
            eventObserver.assertValue(event);
            pipeline.close();
        }
    }

    @Nested
    class Read {
        @Test
        void shouldProcessMemberJoined() {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, renewTasks);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                    config,
                    identity,
                    peersManager,
                    inboundValidator,
                    outboundValidator,
                    handler);
            final TestObserver<Event> eventObserver = pipeline.inboundEvents().test();
            final MemberJoinedMessage msg = new MemberJoinedMessage(publicKey, group);

            final CompletableFuture<Void> future = pipeline.processInbound(publicKey, msg);

            eventObserver.awaitCount(1).assertValueCount(1);
            eventObserver.assertValue(new GroupMemberJoinedEvent(publicKey, group));
            assertTrue(future.isDone());
            pipeline.close();
        }

        @Test
        void shouldProcessMemberLeft() {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, renewTasks);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                    config,
                    identity,
                    peersManager,
                    inboundValidator,
                    outboundValidator,
                    handler);
            final TestObserver<Event> eventObserver = pipeline.inboundEvents().test();
            final MemberLeftMessage msg = new MemberLeftMessage(publicKey, group);

            final CompletableFuture<Void> future = pipeline.processInbound(publicKey, msg);

            eventObserver.awaitCount(1).assertValueCount(1);
            eventObserver.assertValue(new GroupMemberLeftEvent(publicKey, group));
            assertTrue(future.isDone());
            pipeline.close();
        }

        @Test
        void shouldProcessWelcome() {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, renewTasks);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                    config,
                    identity,
                    peersManager,
                    inboundValidator,
                    outboundValidator,
                    handler);
            final TestObserver<Event> eventObserver = pipeline.inboundEvents().test();
            final GroupWelcomeMessage msg = new GroupWelcomeMessage(group, Set.of(publicKey));
            final Duration timeout = Duration.ofSeconds(60);

            when(groups.get(isA(Group.class))).thenReturn(uri);
            when(uri.getTimeout()).thenReturn(timeout);

            final CompletableFuture<Void> future = pipeline.processInbound(publicKey, msg);

            eventObserver.awaitCount(1).assertValueCount(1);
            eventObserver.assertValue(new GroupJoinedEvent(group, Set.of(publicKey), () -> {
            }));
            assertTrue(future.isDone());

            verify(renewTasks).add(any());
            pipeline.close();
        }

        @Test
        void shouldProcessJoinFailed() {
            final GroupsClientHandler handler = new GroupsClientHandler(groups, renewTasks);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(
                    config,
                    identity,
                    peersManager,
                    inboundValidator,
                    outboundValidator,
                    handler);
            final TestObserver<Event> eventObserver = pipeline.inboundEvents().test();
            final GroupJoinFailedMessage.Error error = GroupJoinFailedMessage.Error.ERROR_GROUP_NOT_FOUND;
            final GroupJoinFailedMessage msg = new GroupJoinFailedMessage(group, error);

            final CompletableFuture<Void> future = pipeline.processInbound(publicKey, msg);

            eventObserver.awaitCount(1).assertValueCount(1);
            eventObserver.assertValue(new GroupJoinFailedEvent(group, error, () -> {
            }));

            assertTrue(future.isDone());
            pipeline.close();
        }
    }
}