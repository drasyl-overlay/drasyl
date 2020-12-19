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
package org.drasyl.cli.command.wormhole;

import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.plugin.PluginManager;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.drasyl.util.DrasylScheduler.getInstanceLight;
import static org.drasyl.util.SecretUtil.maskSecret;

@SuppressWarnings({ "java:S107" })
public class ReceivingWormholeNode extends DrasylNode {
    private static final Logger log = LoggerFactory.getLogger(ReceivingWormholeNode.class);
    private final CompletableFuture<Void> doneFuture;
    private final PrintStream printStream;
    private final AtomicBoolean received;
    private CompressedPublicKey sender;
    private Disposable timeoutGuard;

    ReceivingWormholeNode(final CompletableFuture<Void> doneFuture,
                          final PrintStream printStream,
                          final AtomicBoolean received,
                          final CompressedPublicKey sender,
                          final Disposable timeoutGuard,
                          final DrasylConfig config,
                          final Identity identity,
                          final PeersManager peersManager,
                          final Set<Endpoint> endpoints,
                          final AtomicBoolean acceptNewConnections,
                          final Pipeline pipeline,
                          final PluginManager pluginManager,
                          final AtomicBoolean started,
                          final CompletableFuture<Void> startSequence,
                          final CompletableFuture<Void> shutdownSequence) {
        super(config, identity, peersManager, endpoints, acceptNewConnections, pipeline, pluginManager, started, startSequence, shutdownSequence);
        this.doneFuture = doneFuture;
        this.printStream = printStream;
        this.received = received;
        this.sender = sender;
        this.timeoutGuard = timeoutGuard;
    }

    public ReceivingWormholeNode(final DrasylConfig config,
                                 final PrintStream printStream) throws DrasylException {
        super(DrasylConfig.newBuilder(config)
                .remoteBindPort(0)
                .marshallingInboundAllowedTypes(List.of(WormholeMessage.class.getName()))
                .marshallingOutboundAllowedTypes(List.of(WormholeMessage.class.getName()))
                .build());
        this.doneFuture = new CompletableFuture<>();
        this.printStream = printStream;
        this.received = new AtomicBoolean();
    }

    @Override
    public void onEvent(final Event event) {
        if (event instanceof NodeUnrecoverableErrorEvent) {
            doneFuture.completeExceptionally(((NodeUnrecoverableErrorEvent) event).getError());
        }
        else if (event instanceof NodeNormalTerminationEvent) {
            doneFuture.complete(null);
        }
        else if (event instanceof MessageEvent) {
            final CompressedPublicKey messageSender = ((MessageEvent) event).getSender();
            final Object message = ((MessageEvent) event).getPayload();

            if (message instanceof TextMessage && messageSender.equals(this.sender) && received.compareAndSet(false, true)) {
                receivedText((TextMessage) message);
            }
            else if (message instanceof WrongPasswordMessage && messageSender.equals(this.sender) && received.compareAndSet(false, true)) {
                fail();
            }
        }
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        if (timeoutGuard != null && !timeoutGuard.isDisposed()) {
            timeoutGuard.dispose();
        }
        return super.shutdown();
    }

    private void receivedText(final TextMessage message) {
        printStream.println(message.getText());
        doneFuture.complete(null);
    }

    private void fail() {
        doneFuture.completeExceptionally(new Exception(
                "Code confirmation failed. Either you or your correspondent\n" +
                        "typed the code wrong, or a would-be man-in-the-middle attacker guessed\n" +
                        "incorrectly. You could try again, giving both your correspondent and\n" +
                        "the attacker another chance."
        ));
    }

    public CompletableFuture<Void> doneFuture() {
        return doneFuture;
    }

    public void requestText(final CompressedPublicKey sender, final String password) {
        this.sender = sender;
        requestText(sender, password, new AtomicInteger(10));
    }

    public void requestText(final CompressedPublicKey sender,
                            final String password,
                            final AtomicInteger remainingRetries) {
        log.debug("Requesting text from '{}' with password '{}'", () -> sender, () -> maskSecret(password));
        send(sender, new PasswordMessage(password)).whenComplete((result, e) -> {
            if (e != null) {
                if (remainingRetries.decrementAndGet() > 0) {
                    getInstanceLight().scheduleDirect(() -> requestText(sender, password, remainingRetries), 1, SECONDS);
                }
                else {
                    fail();
                }
            }
            else {
                timeoutGuard = getInstanceLight().scheduleDirect(this::fail, 10, SECONDS);
            }
        });
    }
}
