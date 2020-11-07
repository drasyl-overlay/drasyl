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
package org.drasyl.plugin.groups.manager;

import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.SimpleInboundHandler;
import org.drasyl.plugin.groups.client.event.GroupEvent;
import org.drasyl.plugin.groups.client.message.GroupJoinFailedMessage;
import org.drasyl.plugin.groups.client.message.GroupJoinMessage;
import org.drasyl.plugin.groups.client.message.GroupLeaveMessage;
import org.drasyl.plugin.groups.client.message.GroupWelcomeMessage;
import org.drasyl.plugin.groups.client.message.GroupsClientMessage;
import org.drasyl.plugin.groups.client.message.GroupsPluginMessage;
import org.drasyl.plugin.groups.client.message.GroupsServerMessage;
import org.drasyl.plugin.groups.client.message.MemberJoinedMessage;
import org.drasyl.plugin.groups.client.message.MemberLeftMessage;
import org.drasyl.plugin.groups.manager.data.Group;
import org.drasyl.plugin.groups.manager.data.Member;
import org.drasyl.plugin.groups.manager.data.Membership;
import org.drasyl.plugin.groups.manager.database.DatabaseAdapter;
import org.drasyl.plugin.groups.manager.database.DatabaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.drasyl.plugin.groups.client.message.GroupJoinFailedMessage.Error.ERROR_GROUP_NOT_FOUND;
import static org.drasyl.plugin.groups.client.message.GroupJoinFailedMessage.Error.ERROR_PROOF_TO_WEAK;
import static org.drasyl.plugin.groups.client.message.GroupJoinFailedMessage.Error.ERROR_UNKNOWN;

public class GroupsManagerHandler extends SimpleInboundHandler<GroupsClientMessage, GroupEvent, CompressedPublicKey> {
    private static final Logger LOG = LoggerFactory.getLogger(GroupsManagerHandler.class);
    private final DatabaseAdapter database;
    private Disposable staleTask;

    GroupsManagerHandler(final DatabaseAdapter database,
                         final Disposable staleTask) {
        this.database = database;
        this.staleTask = staleTask;
    }

    public GroupsManagerHandler(final DatabaseAdapter database) {
        this(database, null);
    }

    @Override
    public void handlerAdded(final HandlerContext ctx) {
        ctx.inboundValidator().addClass(GroupsClientMessage.class);
        ctx.outboundValidator().addClass(GroupsServerMessage.class);

        // Register stale task timer
        staleTask = ctx.scheduler().schedulePeriodicallyDirect(() -> staleTask(ctx), 1L, 1L, MINUTES);
    }

    /**
     * Deletes the stale memberships.
     *
     * @param ctx the handler context
     */
    void staleTask(final HandlerContext ctx) {
        try {
            for (final Membership member : database.deleteStaleMemberships()) {
                final MemberLeftMessage leftMessage = new MemberLeftMessage(
                        member.getMember().getPublicKey(),
                        org.drasyl.plugin.groups.client.Group.of(member.getGroup().getName()));

                ctx.pipeline().processOutbound(member.getMember().getPublicKey(), leftMessage);
                notifyMembers(ctx, member.getGroup().getName(), leftMessage);
            }
        }
        catch (final DatabaseException e) {
            LOG.warn("Error occurred during deletion of stale memberships: ", e);
        }
    }

    @Override
    public void handlerRemoved(final HandlerContext ctx) {
        if (staleTask != null) {
            staleTask.dispose();
        }
    }

    /**
     * Notifies all members of the given {@code group} with the {@code msg}.
     *
     * @param ctx   the handling context
     * @param group the group that should be notified
     * @param msg   the message that should send to all members of the given {@code group}
     */
    private void notifyMembers(final HandlerContext ctx,
                               final String group,
                               final GroupsPluginMessage msg) throws DatabaseException {
        try {
            final Set<Membership> recipients = database.getGroupMembers(group);

            recipients.forEach(member -> ctx.pipeline().processOutbound(member.getMember().getPublicKey(), msg));
        }
        catch (final DatabaseException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Error occurred on getting members of group '{}': ", group, e);
            }
        }
    }

    @Override
    protected void matchedEventTriggered(final HandlerContext ctx,
                                         final GroupEvent event,
                                         final CompletableFuture<Void> future) {
        ctx.fireEventTriggered(event, future);
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final CompressedPublicKey sender,
                               final GroupsClientMessage msg,
                               final CompletableFuture<Void> future) {
        if (msg instanceof GroupJoinMessage) {
            ctx.scheduler().scheduleDirect(() -> handleJoinRequest(ctx, sender, (GroupJoinMessage) msg, future));
        }
        else if (msg instanceof GroupLeaveMessage) {
            ctx.scheduler().scheduleDirect(() -> handleLeaveRequest(ctx, sender, (GroupLeaveMessage) msg, future));
        }
    }

    /**
     * Handles a join request of the given {@code sender}.
     *
     * @param ctx    the handler context
     * @param sender the sender of the join request
     * @param msg    the join request message
     * @param future the message future
     */
    private void handleJoinRequest(final HandlerContext ctx,
                                   final CompressedPublicKey sender,
                                   final GroupJoinMessage msg,
                                   final CompletableFuture<Void> future) {
        final String groupName = msg.getGroup().getName();
        try {
            final Group group = database.getGroup(groupName);

            if (group != null) {
                if (msg.getProofOfWork().isValid(sender, group.getMinDifficulty())) {
                    doJoin(ctx, sender, group, future);
                }
                else {
                    ctx.pipeline().processOutbound(sender, new GroupJoinFailedMessage(org.drasyl.plugin.groups.client.Group.of(groupName), ERROR_PROOF_TO_WEAK));
                    future.completeExceptionally(new IllegalArgumentException("Member '" + sender + "' does not fulfill requirements of group '" + groupName + "'"));

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Member '{}' does not fulfill requirements of group '{}'", sender, groupName);
                    }
                }
            }
            else {
                ctx.pipeline().processOutbound(sender, new GroupJoinFailedMessage(org.drasyl.plugin.groups.client.Group.of(groupName), ERROR_GROUP_NOT_FOUND));
                future.completeExceptionally(new IllegalArgumentException("There is no group '" + groupName + "'"));

                if (LOG.isDebugEnabled()) {
                    LOG.debug("There is no group '{}'.", groupName);
                }
            }
        }
        catch (final DatabaseException e) {
            future.completeExceptionally(e);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Error occurred on getting group '{}': ", groupName, e);
            }
        }
    }

    /**
     * Handles a leave request of the given {@code sender}.
     *
     * @param ctx    the handler context
     * @param sender the sender of the leave request
     * @param msg    the leave request
     * @param future the message future
     */
    private void handleLeaveRequest(final HandlerContext ctx,
                                    final CompressedPublicKey sender,
                                    final GroupLeaveMessage msg,
                                    final CompletableFuture<Void> future) {
        try {
            final MemberLeftMessage leftMessage = new MemberLeftMessage(sender, msg.getGroup());

            database.removeGroupMember(sender, msg.getGroup().getName());
            ctx.pipeline().processOutbound(sender, leftMessage);
            notifyMembers(ctx, msg.getGroup().getName(), leftMessage);

            future.complete(null);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Removed member '{}' from group '{}'", sender, msg.getGroup().getName());
            }
        }
        catch (final DatabaseException e) {
            future.completeExceptionally(e);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Error occurred during removal of member '{}' from group '{}': ", sender, msg.getGroup().getName(), e);
            }
        }
    }

    /**
     * This message does the actual join state transition.
     *
     * @param ctx    the handler context
     * @param sender the sender
     * @param group  the group to join
     * @param future the message future
     */
    private void doJoin(final HandlerContext ctx,
                        final CompressedPublicKey sender,
                        final Group group,
                        final CompletableFuture<Void> future) {
        try {
            if (database.addGroupMember(
                    Membership.of(
                            Member.of(sender),
                            group,
                            System.currentTimeMillis() + group.getTimeout().toMillis()))) {
                final Set<Membership> memberships = database.getGroupMembers(group.getName());
                ctx.pipeline().processOutbound(sender, new GroupWelcomeMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()), memberships.stream()
                        .filter(v -> !v.getMember().getPublicKey().equals(sender) && v.getStaleAt() > System.currentTimeMillis())
                        .map(v -> v.getMember().getPublicKey())
                        .collect(Collectors.toSet())));
                notifyMembers(ctx, group.getName(), new MemberJoinedMessage(sender, org.drasyl.plugin.groups.client.Group.of(group.getName())));

                future.complete(null);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Added member '{}' to group '{}'", sender, group.getName());
                }
            }
            else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Renewed membership of '{}' for group '{}'", sender, group.getName());
                }
            }
        }
        catch (final DatabaseException e) {
            ctx.pipeline().processOutbound(sender, new GroupJoinFailedMessage(org.drasyl.plugin.groups.client.Group.of(group.getName()), ERROR_UNKNOWN));
            future.completeExceptionally(e);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Error occurred during join: ", e);
            }
        }
    }
}