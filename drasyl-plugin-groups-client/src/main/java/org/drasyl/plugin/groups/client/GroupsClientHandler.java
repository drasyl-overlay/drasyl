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
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
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
import org.drasyl.serialization.JacksonJsonSerializer;
import org.drasyl.util.FutureUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class GroupsClientHandler extends SimpleInboundEventAwareHandler<GroupsServerMessage, NodeUpEvent, Address> {
    private static final Logger LOG = LoggerFactory.getLogger(GroupsClientHandler.class);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(10);
    private static final Duration FIRST_JOIN_DELAY = Duration.ofSeconds(5);
    private final Duration firstJoinDelay;
    private final Map<Group, GroupUri> groups;
    private final Map<Group, Disposable> renewTasks;

    @SuppressWarnings("java:S2384")
    GroupsClientHandler(final Map<Group, GroupUri> groups,
                        final Map<Group, Disposable> renewTasks,
                        final Duration firstJoinDelay) {
        this.groups = requireNonNull(groups);
        this.renewTasks = requireNonNull(renewTasks);
        this.firstJoinDelay = requireNonNull(firstJoinDelay);
    }

    public GroupsClientHandler(final Set<GroupUri> groups) {
        this(groups.stream().collect(Collectors.toMap(GroupUri::getGroup, groupURI -> groupURI)),
                new ConcurrentHashMap<>(), FIRST_JOIN_DELAY);
    }

    @Override
    public void handlerAdded(final HandlerContext ctx) {
        ctx.inboundSerialization().addSerializer(GroupsServerMessage.class, new JacksonJsonSerializer());
        ctx.outboundSerialization().addSerializer(GroupsClientMessage.class, new JacksonJsonSerializer());
    }

    @Override
    public void handlerRemoved(final HandlerContext ctx) {
        // Stop all renew tasks
        for (final Disposable renewTask : renewTasks.values()) {
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
                ctx.pipeline().processOutbound(groupURI.getManager(), new GroupLeaveMessage(group)).get();
            }
            catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("Exception occurred during de-registration from group '{}': ", group.getName(), e);
            }
            catch (final ExecutionException e) {
                LOG.warn("Exception occurred during de-registration from group '{}': ", group.getName(), e);
            }
        }
    }

    @Override
    protected void matchedEventTriggered(final HandlerContext ctx,
                                         final NodeUpEvent event,
                                         final CompletableFuture<Void> future) {
        // join every group but we will wait 5 seconds, to give it the chance to connect to some super peer if needed
        ctx.independentScheduler().scheduleDirect(() -> groups.values().forEach(group ->
                joinGroup(ctx, group, false)), firstJoinDelay.toMillis(), MILLISECONDS);

        ctx.fireEventTriggered(event, future);
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final GroupsServerMessage msg,
                               final CompletableFuture<Void> future) {
        if (msg instanceof MemberJoinedMessage) {
            onMemberJoined(ctx, (MemberJoinedMessage) msg, future);
        }
        else if (msg instanceof MemberLeftMessage) {
            onMemberLeft(ctx, (MemberLeftMessage) msg, future);
        }
        else if (msg instanceof GroupWelcomeMessage) {
            onWelcome(ctx, sender, (GroupWelcomeMessage) msg, future);
        }
        else if (msg instanceof GroupJoinFailedMessage) {
            onJoinFailed(ctx, (GroupJoinFailedMessage) msg, future);
        }
    }

    /**
     * Will be executed on {@link MemberJoinedMessage}.
     *
     * @param ctx    the handling context
     * @param msg    the member joined message
     * @param future the message future
     */
    private static void onMemberJoined(final HandlerContext ctx,
                                       final MemberJoinedMessage msg,
                                       final CompletableFuture<Void> future) {
        FutureUtil.completeOnAllOf(future,
                ctx.pipeline().processInbound(new GroupMemberJoinedEvent(msg.getMember(), msg.getGroup())));
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

        // cancel renew task
        final Disposable disposable = renewTasks.remove(group);
        if (disposable != null) {
            disposable.dispose();
        }

        FutureUtil.completeOnAllOf(future, ctx.pipeline().processInbound(new GroupJoinFailedEvent(group, msg.getReason(),
                () -> joinGroup(ctx, groups.get(group), false))));
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
            // cancel renew task
            final Disposable disposable = renewTasks.remove(group);
            if (disposable != null) {
                disposable.dispose();
            }

            FutureUtil.completeOnAllOf(future,
                    ctx.pipeline().processInbound(new GroupLeftEvent(group, () -> joinGroup(ctx, groups.get(group), false))));
        }
        else {
            FutureUtil.completeOnAllOf(future,
                    ctx.pipeline().processInbound(new GroupMemberLeftEvent(msg.getMember(), group)));
        }
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
                           final Address sender,
                           final GroupWelcomeMessage msg,
                           final CompletableFuture<Void> future) {
        final Group group = msg.getGroup();
        final Duration timeout = groups.get(group).getTimeout();

        // replace old re-try task with renew task
        final Disposable disposable = renewTasks.remove(group);
        if (disposable != null) {
            disposable.dispose();
        }

        // Add renew task
        renewTasks.put(group, ctx.independentScheduler().schedulePeriodicallyDirect(() ->
                        joinGroup(ctx, groups.get(group), true),
                timeout.dividedBy(2).toMillis(), timeout.dividedBy(2).toMillis(), MILLISECONDS));

        FutureUtil.completeOnAllOf(future, ctx.pipeline().processInbound(
                new GroupJoinedEvent(
                        group,
                        msg.getMembers(),
                        () -> ctx.pipeline().processOutbound(sender, new GroupLeaveMessage(group)))));
    }

    /**
     * Joins the given {@code group}.
     *
     * @param ctx   the handler context
     * @param group the group to join
     * @param renew if this is a renew message or not
     */
    private void joinGroup(final HandlerContext ctx,
                           final GroupUri group,
                           final boolean renew) {
        final ProofOfWork proofOfWork = ctx.identity().getProofOfWork();
        final CompressedPublicKey groupManager = group.getManager();

        ctx.pipeline().processOutbound(groupManager, new GroupJoinMessage(group.getGroup(), group.getCredentials(), proofOfWork, renew));

        // Add re-try task
        if (!renewTasks.containsKey(group.getGroup())) {
            renewTasks.put(group.getGroup(), ctx.independentScheduler().schedulePeriodicallyDirect(() ->
                            joinGroup(ctx, groups.get(group.getGroup()), false),
                    RETRY_DELAY.toMillis(),
                    RETRY_DELAY.toMillis(), MILLISECONDS));
        }

        LOG.debug("Send join (renew={}) request for group '{}'", () -> renew, () -> group);
    }
}
