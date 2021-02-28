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
package org.drasyl.cli.command;

import org.apache.commons.cli.CommandLine;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.annotation.NonNull;
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
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.failedFuture;

/**
 * Run a drasyl node.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class NodeCommand extends AbstractCommand {
    private static final Logger LOG = LoggerFactory.getLogger(NodeCommand.class);
    private final Function<DrasylConfig, Pair<DrasylNode, CompletableFuture<Void>>> nodeSupplier;
    private final Consumer<Integer> exitSupplier;
    private DrasylNode node;

    public NodeCommand() {
        this(
                System.out, // NOSONAR
                System.err, // NOSONAR
                config -> {
                    try {
                        final CompletableFuture<Void> running = new CompletableFuture<>();
                        final DrasylNode myNode = new DrasylNode(config) {
                            @Override
                            public void onEvent(final @NonNull Event event) {
                                LOG.info("Event received: {}", event);
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
                System::exit,
                null
        );
    }

    NodeCommand(final PrintStream out,
                final PrintStream err,
                final Function<DrasylConfig, Pair<DrasylNode, CompletableFuture<Void>>> nodeSupplier,
                final Consumer<Integer> exitSupplier,
                final DrasylNode node) {
        super(out, err);
        this.nodeSupplier = requireNonNull(nodeSupplier);
        this.exitSupplier = requireNonNull(exitSupplier);
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
    public void execute(final CommandLine cmd) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (node != null) {
                LOG.info("Shutdown drasyl Node");
                node.shutdown().join();
            }
        }));

        try {
            final DrasylConfig config = getDrasylConfig(cmd);
            final Pair<DrasylNode, CompletableFuture<Void>> pair = nodeSupplier.apply(config);
            node = pair.first();
            final CompletableFuture<Void> running = pair.second();
            // block while node is running
            running.get();
        }
        catch (final ExecutionException e) {
            throw new CliException(e);
        }
        catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        finally {
            if (node != null) {
                node.shutdown().join();
            }

            exitSupplier.accept(0);
        }
    }

    @Override
    public String getDescription() {
        return "Run a drasyl node.";
    }
}
