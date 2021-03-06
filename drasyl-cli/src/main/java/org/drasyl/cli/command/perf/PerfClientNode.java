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
package org.drasyl.cli.command.perf;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.behaviour.Behavior;
import org.drasyl.behaviour.BehavioralDrasylNode;
import org.drasyl.behaviour.Behaviors;
import org.drasyl.cli.command.perf.message.PerfMessage;
import org.drasyl.cli.command.perf.message.SessionConfirmation;
import org.drasyl.cli.command.perf.message.SessionRejection;
import org.drasyl.cli.command.perf.message.SessionRequest;
import org.drasyl.event.Event;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.event.PeerDirectEvent;
import org.drasyl.event.PeerEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.plugin.PluginManager;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static org.drasyl.behaviour.Behaviors.ignore;
import static org.drasyl.behaviour.Behaviors.same;
import static org.drasyl.serialization.Serializers.SERIALIZER_JACKSON_JSON;

/**
 * Connects to a {@link PerfServerNode} and performs a connection test.
 *
 * <p>The client requests a session from the server. If the session is confirmed by the server, the
 * client performs the performance test, and shuts down after completion.
 */
public class PerfClientNode extends BehavioralDrasylNode {
    private static final Logger LOG = LoggerFactory.getLogger(PerfClientNode.class);
    public static final Duration ONLINE_TIMEOUT = ofSeconds(10);
    private static final Duration REQUEST_SESSION_TIMEOUT = ofSeconds(10);
    private final CompletableFuture<Void> doneFuture;
    private final PrintStream printStream;
    private final Scheduler perfScheduler;
    private final Set<CompressedPublicKey> directConnections;
    private TestOptions testOptions;

    @SuppressWarnings({ "java:S107", "java:S2384" })
    PerfClientNode(final CompletableFuture<Void> doneFuture,
                   final PrintStream printStream,
                   final Scheduler perfScheduler,
                   final Set<CompressedPublicKey> directConnections,
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
        this.printStream = requireNonNull(printStream);
        this.perfScheduler = requireNonNull(perfScheduler);
        this.directConnections = requireNonNull(directConnections);
    }

    public PerfClientNode(final DrasylConfig config,
                          final PrintStream printStream) throws DrasylException {
        super(DrasylConfig.newBuilder(config)
                .addSerializationsBindingsInbound(PerfMessage.class, SERIALIZER_JACKSON_JSON)
                .addSerializationsBindingsOutbound(PerfMessage.class, SERIALIZER_JACKSON_JSON)
                .build());
        this.doneFuture = new CompletableFuture<>();
        this.printStream = requireNonNull(printStream);
        perfScheduler = Schedulers.io();
        directConnections = new HashSet<>();
    }

    @Override
    protected Behavior created() {
        return offline();
    }

    public CompletableFuture<Void> doneFuture() {
        return doneFuture;
    }

    /**
     * @throws NullPointerException if {@code server} is {@code null}
     */
    public void setTestOptions(final CompressedPublicKey server,
                               final int testDuration,
                               final int messagesPerSecond,
                               final int messageSize,
                               final boolean directConnection, final boolean reverse) {
        onEvent(new TestOptions(server, testDuration, messagesPerSecond, messageSize, directConnection, reverse));
    }

    /**
     * Node is not connected to super peer (node must be online to perform a performance test).
     */
    private Behavior offline() {
        return Behaviors.receive()
                .onEvent(TestOptions.class, event -> testOptions == null, event -> {
                    // we are not online (yet), remember server for later
                    this.testOptions = event;
                    return same();
                })
                .onEvent(NodeUpEvent.class, event -> Behaviors.withScheduler(eventScheduler -> {
                    eventScheduler.scheduleEvent(new OnlineTimeout(), ONLINE_TIMEOUT);
                    return offline();
                }))
                .onEvent(NodeUnrecoverableErrorEvent.class, event -> {
                    doneFuture.completeExceptionally(event.getError());
                    return ignore();
                })
                .onEvent(NodeNormalTerminationEvent.class, event -> {
                    doneFuture.complete(null);
                    return ignore();
                })
                .onEvent(NodeOnlineEvent.class, event -> online())
                .onEvent(PeerEvent.class, this::handlePeerEvent)
                .onEvent(OnlineTimeout.class, event -> {
                    doneFuture.completeExceptionally(new Exception("Client did not come online within " + ONLINE_TIMEOUT.toSeconds() + "s. Look like super peer is unavailable."));
                    return ignore();
                })
                .onAnyEvent(event -> same())
                .build();
    }

    /**
     * Node is connected to super peer (ready to request session at server).
     */
    private Behavior online() {
        if (testOptions != null) {
            // server is known, we can request session now
            return requestSession();
        }
        else {
            // server is not known yet, wait for it
            return Behaviors.receive()
                    .onEvent(TestOptions.class, event -> testOptions == null, event -> {
                        this.testOptions = event;
                        return requestSession();
                    })
                    .onEvent(PeerEvent.class, this::handlePeerEvent)
                    .onAnyEvent(event -> same())
                    .build();
        }
    }

    /**
     * Node is requesting sessions at super peer and waiting for response(s).
     */
    private Behavior requestSession() {
        if (testOptions.requireDirectConnection() && !directConnections.contains(testOptions.getServer())) {
            return initiateDirectConnection();
        }
        else {
            return Behaviors.withScheduler(eventScheduler -> {
                printStream.println("Connecting to " + testOptions.getServer() + "...");

                // timeout guard
                eventScheduler.scheduleEvent(new RequestSessionTimeout(), REQUEST_SESSION_TIMEOUT);

                // request session
                final SessionRequest session = new SessionRequest(testOptions.getTestDuration(), testOptions.getMessagesPerSecond(), testOptions.getMessageSize(), testOptions.reverse());
                LOG.debug("Request session at {}", testOptions.getServer());
                send(testOptions.getServer(), session);

                // new behavior
                return Behaviors.receive()
                        .onMessage(SessionConfirmation.class, (sender, payload) -> sender.equals(testOptions.getServer()), (sender, payload) -> {
                            // session confirmed
                            LOG.debug("Session has been confirmed by {}", sender);
                            printStream.println("Connected to " + testOptions.getServer() + "!");
                            return startSession(session);
                        })
                        .onMessage(SessionRejection.class, (sender, payload) -> sender.equals(testOptions.getServer()), (sender, payload) -> {
                            // session rejected
                            doneFuture.completeExceptionally(new Exception("The server is busy running a test. Try again later."));
                            return ignore();
                        })
                        .onEvent(RequestSessionTimeout.class, event -> {
                            // no response
                            doneFuture.completeExceptionally(new Exception("The server did not respond within " + REQUEST_SESSION_TIMEOUT.toSeconds() + "s. Try again later."));
                            return ignore();
                        })
                        .onEvent(PeerEvent.class, this::handlePeerEvent)
                        .onAnyEvent(event -> same())
                        .build();
            });
        }
    }

    /**
     * Node is waiting for a direct connection to be established.
     */
    private Behavior initiateDirectConnection() {
        // send empty message to trigger rendezvous
        send(testOptions.getServer(), new byte[0]);

        return Behaviors.withScheduler(eventScheduler -> {
            // timeout guard
            eventScheduler.scheduleEvent(new DirectConnectionTimeout(), REQUEST_SESSION_TIMEOUT);

            return Behaviors.receive()
                    .onEvent(PeerEvent.class, event -> {
                        handlePeerEvent(event);
                        return requestSession();
                    })
                    .onEvent(DirectConnectionTimeout.class, event -> {
                        // no response
                        doneFuture.completeExceptionally(new Exception("No direct connection to the server could be established within " + REQUEST_SESSION_TIMEOUT.toSeconds() + "s. Try again later."));
                        return ignore();
                    })
                    .onAnyEvent(event -> same())
                    .build();
        });
    }

    /**
     * Node is doing a performance test with the server.
     */
    private Behavior startSession(final SessionRequest session) {
        final Supplier<Behavior> successBehavior = () -> {
            doneFuture.complete(null);
            return ignore();
        };
        final Function<Exception, Behavior> failureBehavior = e -> {
            doneFuture.completeExceptionally(e);
            return ignore();
        };

        if (!session.isReverse()) {
            final PerfTestSender sender = new PerfTestSender(testOptions.getServer(), session, perfScheduler, printStream, this::send, successBehavior, failureBehavior);
            return sender.run();
        }
        else {
            printStream.println("Reverse mode, server is sending");
            final PerfTestReceiver receiver = new PerfTestReceiver(testOptions.getServer(), session, perfScheduler, printStream, this::send, successBehavior, failureBehavior);
            return receiver.run();
        }
    }

    /**
     * Handles the change of a peer connection type (direct vs relayed)
     */
    Behavior handlePeerEvent(final PeerEvent event) {
        if (event instanceof PeerDirectEvent) {
            directConnections.add(event.getPeer().getPublicKey());
        }
        else {
            directConnections.remove(event.getPeer().getPublicKey());
        }
        return same();
    }

    /**
     * Signals that the server has been set.
     */
    @SuppressWarnings("java:S2972")
    static class TestOptions implements Event {
        private final CompressedPublicKey server;
        private final int messagesPerSecond;
        private final int testDuration;
        private final int messageSize;
        private final boolean directConnection;
        private final boolean reverse;

        /**
         * @throws NullPointerException if {@code server} is {@code null}
         */
        public TestOptions(final CompressedPublicKey server,
                           final int testDuration,
                           final int messagesPerSecond,
                           final int messageSize,
                           final boolean directConnection, final boolean reverse) {
            this.server = requireNonNull(server);
            this.testDuration = testDuration;
            this.messagesPerSecond = messagesPerSecond;
            this.messageSize = messageSize;
            this.directConnection = directConnection;
            this.reverse = reverse;
        }

        public CompressedPublicKey getServer() {
            return server;
        }

        public int getMessagesPerSecond() {
            return messagesPerSecond;
        }

        public int getTestDuration() {
            return testDuration;
        }

        public int getMessageSize() {
            return messageSize;
        }

        public boolean requireDirectConnection() {
            return directConnection;
        }

        public boolean reverse() {
            return reverse;
        }
    }

    /**
     * Signals that the server could not go online.
     */
    static class OnlineTimeout implements Event {
    }

    /**
     * Signals that no direct connection to the super could be established.
     */
    static class DirectConnectionTimeout implements Event {
    }

    /**
     * Signals that the server has not responded to the session request.
     */
    static class RequestSessionTimeout implements Event {
    }
}
