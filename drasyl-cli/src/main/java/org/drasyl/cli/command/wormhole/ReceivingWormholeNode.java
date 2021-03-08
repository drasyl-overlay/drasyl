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
package org.drasyl.cli.command.wormhole;

import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.behaviour.Behavior;
import org.drasyl.behaviour.BehavioralDrasylNode;
import org.drasyl.behaviour.Behaviors;
import org.drasyl.event.Event;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.plugin.PluginManager;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static org.drasyl.behaviour.Behaviors.ignore;
import static org.drasyl.behaviour.Behaviors.same;
import static org.drasyl.serialization.Serializers.SERIALIZER_JACKSON_JSON;
import static org.drasyl.util.SecretUtil.maskSecret;

@SuppressWarnings({ "java:S107" })
public class ReceivingWormholeNode extends BehavioralDrasylNode {
    public static final Duration ONLINE_TIMEOUT = ofSeconds(10);
    public static final Duration REQUEST_TEXT_TIMEOUT = ofSeconds(10);
    private static final Logger LOG = LoggerFactory.getLogger(ReceivingWormholeNode.class);
    private final CompletableFuture<Void> doneFuture;
    private final PrintStream out;
    private RequestText request;

    ReceivingWormholeNode(final CompletableFuture<Void> doneFuture,
                          final PrintStream out,
                          final RequestText request,
                          final DrasylConfig config,
                          final Identity identity,
                          final PeersManager peersManager,
                          final Pipeline pipeline,
                          final PluginManager pluginManager,
                          final AtomicReference<CompletableFuture<Void>> startFuture,
                          final AtomicReference<CompletableFuture<Void>> shutdownFuture,
                          final Scheduler scheduler) {
        super(config, identity, peersManager, pipeline, pluginManager, startFuture, shutdownFuture, scheduler);
        this.doneFuture = requireNonNull(doneFuture);
        this.out = requireNonNull(out);
        this.request = request;
    }

    public ReceivingWormholeNode(final DrasylConfig config,
                                 final PrintStream out) throws DrasylException {
        super(DrasylConfig.newBuilder(config)
                .addSerializationsBindingsInbound(WormholeMessage.class, SERIALIZER_JACKSON_JSON)
                .addSerializationsBindingsOutbound(WormholeMessage.class, SERIALIZER_JACKSON_JSON)
                .build());
        this.doneFuture = new CompletableFuture<>();
        this.out = requireNonNull(out);
    }

    @Override
    protected Behavior created() {
        return offline();
    }

    public CompletableFuture<Void> doneFuture() {
        return doneFuture;
    }

    /**
     * @throws NullPointerException if {@code sender} or {@code text} is {@code null}
     */
    public void requestText(final CompressedPublicKey sender, final String password) {
        onEvent(new RequestText(sender, password));
    }

    /**
     * Node is not connected to super peer.
     */
    private Behavior offline() {
        return Behaviors.withScheduler(scheduler -> {
            if (request != null) {
                scheduler.scheduleEvent(new OnlineTimeout(), ONLINE_TIMEOUT);
            }

            return Behaviors.receive()
                    .onEvent(NodeUnrecoverableErrorEvent.class, event -> {
                        doneFuture.completeExceptionally(event.getError());
                        return ignore();
                    })
                    .onEvent(NodeNormalTerminationEvent.class, event -> terminate())
                    .onEvent(NodeOnlineEvent.class, event -> online())
                    .onEvent(OnlineTimeout.class, event -> {
                        doneFuture.completeExceptionally(new Exception("Node did not come online within " + ONLINE_TIMEOUT.toSeconds() + "s. Look like super peer is unavailable."));
                        return ignore();
                    })
                    .onEvent(RequestText.class, event -> request == null, event -> {
                        // we are not online (yet), remember for later
                        this.request = event;
                        return offline();
                    })
                    .onAnyEvent(event -> same())
                    .build();
        });
    }

    /**
     * Node is connected to super peer.
     */
    private Behavior online() {
        if (request != null) {
            // sender and password are known, we can request text
            return requestText(request);
        }
        else {
            // sender and password are not known yet, wait for both
            return Behaviors.receive()
                    .onEvent(NodeNormalTerminationEvent.class, event -> terminate())
                    .onEvent(NodeOfflineEvent.class, event -> offline())
                    .onEvent(RequestText.class, event -> request == null, event -> {
                        this.request = event;
                        return requestText(request);
                    })
                    .onAnyEvent(event -> same())
                    .build();
        }
    }

    /**
     * Node is requesting text from {@link SendingWormholeNode} and waiting for the response.
     */
    private Behavior requestText(final RequestText request) {
        LOG.debug("Requesting text from '{}' with password '{}'", request::getSender, () -> maskSecret(request.getPassword()));
        send(request.getSender(), new PasswordMessage(request.getPassword())).join();

        return Behaviors.withScheduler(scheduler -> {
            scheduler.scheduleEvent(new RequestTextTimeout(), REQUEST_TEXT_TIMEOUT);

            return Behaviors.receive()
                    .onEvent(NodeNormalTerminationEvent.class, event -> terminate())
                    .onEvent(RequestTextTimeout.class, event -> fail())
                    .onMessage(TextMessage.class, (sender, payload) -> sender.equals(request.getSender()), (sender, payload) -> {
                        LOG.debug("Got text from '{}': {}", () -> sender, payload::getText);
                        out.println(payload.getText());
                        return terminate();
                    })
                    .onMessage(WrongPasswordMessage.class, (sender, payload) -> sender.equals(request.getSender()), (sender, message) -> fail())
                    .onAnyEvent(event -> same())
                    .build();
        });
    }

    /**
     * Terminate this node.
     */
    private Behavior terminate() {
        doneFuture.complete(null);
        return ignore();
    }

    /**
     * Transfer failed.
     */
    private Behavior fail() {
        doneFuture.completeExceptionally(new Exception("Code confirmation failed. Either you or your correspondent\n" +
                "typed the code wrong, or a would-be man-in-the-middle attacker guessed\n" +
                "incorrectly. You could try again, giving both your correspondent and\n" +
                "the attacker another chance."));
        return ignore();
    }

    /**
     * Signals that text has should be requested.
     */
    static class RequestText implements Event {
        private final CompressedPublicKey sender;
        private final String password;

        /**
         * @throws NullPointerException if {@code sender} or {@code text} is {@code null}
         */
        public RequestText(final CompressedPublicKey sender, final String password) {
            this.sender = requireNonNull(sender);
            this.password = requireNonNull(password);
        }

        CompressedPublicKey getSender() {
            return sender;
        }

        String getPassword() {
            return password;
        }
    }

    /**
     * Signals that the node could not go online.
     */
    static class OnlineTimeout implements Event {
    }

    /**
     * Signals that sending node has not send the requested text.
     */
    private static class RequestTextTimeout implements Event {
    }
}
