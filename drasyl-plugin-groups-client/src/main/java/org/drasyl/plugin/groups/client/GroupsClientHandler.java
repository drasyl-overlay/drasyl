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
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.skeleton.SimpleInboundEventAwareHandler;
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
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class GroupsClientHandler extends SimpleInboundEventAwareHandler<GroupsServerMessage, NodeOnlineEvent, CompressedPublicKey> {
    private static final Logger LOG = LoggerFactory.getLogger(GroupsClientHandler.class);
    private final Map<Group, GroupUri> groups;
    private final List<Disposable> renewTasks;

    GroupsClientHandler(final Map<Group, GroupUri> groups, final List<Disposable> renewTasks) {
        this.groups = groups;
        this.renewTasks = renewTasks;
    }

    public GroupsClientHandler(final Set<GroupUri> groups) {
        this(groups.stream().collect(Collectors.toMap(GroupUri::getGroup, groupURI -> groupURI)), new ArrayList<>());
    }

    @Override
    public void handlerAdded(final HandlerContext ctx) {
        ctx.inboundValidator().addClass(GroupsServerMessage.class);
        ctx.outboundValidator().addClass(GroupsClientMessage.class);
    }

    @Override
    public void handlerRemoved(final HandlerContext ctx) {
        // Stop all renew tasks
        for (final Disposable renewTask : renewTasks) {
            renewTask.dispose();
        }
        renewTasks.clear();

        /*
         * Leave all groups.
         * This should be a blocking operation, so drasyl is not shutting down the communication
         * channel before the leave messages are sent.
         */
        for (final Entry<Group, GroupUri> entry : groups.entrySet()) {
            final Group group = entry.getKey();
            final GroupUri groupURI = entry.getValue();
            try {
                ctx.pipeline().processOutbound(groupURI.getManager(), new GroupLeaveMessage(group)).join();
            }
            catch (final Exception e) {
                LOG.warn("Exception occurred during de-registration from group '{}': ", group.getName(), e);
            }
        }
    }

    @Override
    protected void matchedEventTriggered(final HandlerContext ctx,
                                         final NodeOnlineEvent event,
                                         final CompletableFuture<Void> future) {
        // join every group
        ctx.scheduler().scheduleDirect(() -> groups.values().forEach(group -> joinGroup(ctx, group)));
        ctx.fireEventTriggered(event, future);
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final CompressedPublicKey sender,
                               final GroupsServerMessage msg,
                               final CompletableFuture<Void> future) {
        if (msg instanceof MemberJoinedMessage) {
            ctx.scheduler().scheduleDirect(() -> onMemberJoined(ctx, (MemberJoinedMessage) msg, future));
        }
        else if (msg instanceof MemberLeftMessage) {
            ctx.scheduler().scheduleDirect(() -> onMemberLeft(ctx, (MemberLeftMessage) msg, future));
        }
        else if (msg instanceof GroupWelcomeMessage) {
            ctx.scheduler().scheduleDirect(() -> onWelcome(ctx, sender, (GroupWelcomeMessage) msg, future));
        }
        else if (msg instanceof GroupJoinFailedMessage) {
            ctx.scheduler().scheduleDirect(() -> onJoinFailed(ctx, (GroupJoinFailedMessage) msg, future));
        }
    }

    /**
     * Will be executed on {@link MemberJoinedMessage}.
     *
     * @param ctx    the handling context
     * @param msg    the member joined message
     * @param future the message future
     */
    private void onMemberJoined(final HandlerContext ctx,
                                final MemberJoinedMessage msg,
                                final CompletableFuture<Void> future) {
        ctx.pipeline().processInbound(new GroupMemberJoinedEvent(msg.getMember(), msg.getGroup()));
        future.complete(null);
    }

    /**
     * Will be executed on {@link GroupJoinFailedMessage}.
     *
     * @param ctx    the handling context
     * @param msg    the join failed message
     * @param future the message future
     */
    private void onJoinFailed(final HandlerContext ctx,
                              final GroupJoinFailedMessage msg,
                              final CompletableFuture<Void> future) {
        final Group group = msg.getGroup();

        ctx.pipeline().processInbound(new GroupJoinFailedEvent(group, msg.getReason(), () -> this.joinGroup(ctx, groups.get(group))));
        future.complete(null);
    }

    /**
     * Will be executed on {@link MemberLeftMessage}.
     *
     * @param ctx    the handling context
     * @param msg    the member left message
     * @param future the message future
     */
    private void onMemberLeft(final HandlerContext ctx,
                              final MemberLeftMessage msg,
                              final CompletableFuture<Void> future) {
        final Group group = msg.getGroup();

        if (msg.getMember().equals(ctx.identity().getPublicKey())) {
            ctx.pipeline().processInbound(new GroupLeftEvent(group, () -> this.joinGroup(ctx, groups.get(group))));
        }
        else {
            ctx.pipeline().processInbound(new GroupMemberLeftEvent(msg.getMember(), group));
        }

        future.complete(null);
    }

    /**
     * Will be executed on {@link GroupWelcomeMessage}.
     *
     * @param ctx    the handling context
     * @param sender the sender (group manager)
     * @param msg    the welcome message
     * @param future the message future
     */
    private void onWelcome(final HandlerContext ctx,
                           final CompressedPublicKey sender,
                           final GroupWelcomeMessage msg,
                           final CompletableFuture<Void> future) {
        final Group group = msg.getGroup();
        final Duration timeout = groups.get(group).getTimeout();

        // Add renew task
        renewTasks.add(ctx.scheduler().schedulePeriodicallyDirect(() ->
                        this.joinGroup(ctx, groups.get(group)),
                timeout.dividedBy(2).toMillis(), timeout.dividedBy(2).toMillis(), MILLISECONDS));

        ctx.pipeline().processInbound(
                new GroupJoinedEvent(
                        group,
                        msg.getMembers(),
                        () -> ctx.pipeline().processOutbound(sender, new GroupLeaveMessage(group))));
        future.complete(null);
    }

    /**
     * Joins the given {@code group}.
     *
     * @param ctx   the handler context
     * @param group the group to join
     */
    private void joinGroup(final HandlerContext ctx, final GroupUri group) {
        final ProofOfWork proofOfWork = ctx.identity().getProofOfWork();
        final CompressedPublicKey groupManager = group.getManager();

        ctx.pipeline().processOutbound(groupManager, new GroupJoinMessage(group.getGroup(), group.getCredentials(), proofOfWork));
    }
}
