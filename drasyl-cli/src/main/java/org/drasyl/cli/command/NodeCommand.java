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

package org.drasyl.cli.command;

import io.sentry.Sentry;
import org.apache.commons.cli.CommandLine;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.cli.CliException;
import org.drasyl.event.Event;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.failedFuture;

/**
 * Run a drasyl node.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class NodeCommand extends AbstractCommand {
    private static final Logger log = LoggerFactory.getLogger(NodeCommand.class);

    static {
        Sentry.init(options -> {
            options.setEnableExternalConfiguration(true);
            options.setRelease(DrasylNode.getVersion());
        });
    }

    private final Function<DrasylConfig, Pair<DrasylNode, CompletableFuture<Void>>> nodeSupplier;
    private DrasylNode node;

    public NodeCommand() {
        this(
                System.out, // NOSONAR
                config -> {
                    try {
                        final CompletableFuture<Void> running = new CompletableFuture<>();
                        final DrasylNode myNode = new DrasylNode(config) {
                            @Override
                            public void onEvent(final Event event) {
                                log.info("Event received: {}", event);
                                if (event instanceof NodeNormalTerminationEvent) {
                                    running.complete(null);
                                }
                                else if (event instanceof NodeUnrecoverableErrorEvent) {
                                    running.completeExceptionally(((NodeUnrecoverableErrorEvent) event).getError());
                                }
                            }
                        };
                        myNode.start();
                        return Pair.of(myNode, running);
                    }
                    catch (final DrasylException e) {
                        return Pair.of(null, failedFuture(e));
                    }
                },
                null
        );
    }

    NodeCommand(final PrintStream printStream,
                final Function<DrasylConfig, Pair<DrasylNode, CompletableFuture<Void>>> nodeSupplier,
                final DrasylNode node) {
        super(printStream);
        this.nodeSupplier = nodeSupplier;
        this.node = node;
    }

    @SuppressWarnings({ "java:S1192" })
    @Override
    protected void help(final CommandLine cmd) {
        helpTemplate(
                "node",
                "Run a drasyl node in the current directory."
        );
    }

    @Override
    public void execute(final CommandLine cmd) throws CliException {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (node != null) {
                log.info("Shutdown drasyl Node");
                node.shutdown().join();
            }
        }));

        try {
            final DrasylConfig config = getDrasylConfig(cmd);
            final Pair<DrasylNode, CompletableFuture<Void>> pair = nodeSupplier.apply(config);
            node = pair.first();
            final CompletableFuture<Void> running = pair.second();
            running.get(); // block while node is running
        }
        catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (final ExecutionException e) {
            throw new CliException(e);
        }
    }

    @Override
    public String getDescription() {
        return "Run a drasyl node.";
    }
}