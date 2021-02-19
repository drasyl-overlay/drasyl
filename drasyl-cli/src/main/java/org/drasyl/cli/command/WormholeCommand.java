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
import org.drasyl.cli.CliException;
import org.drasyl.cli.command.wormhole.ReceivingWormholeNode;
import org.drasyl.cli.command.wormhole.SendingWormholeNode;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.util.ThrowingFunction;
import org.drasyl.util.Triple;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.drasyl.identity.CompressedPublicKey.PUBLIC_KEY_LENGTH;

/**
 * Inspired by <a href="https://github.com/warner/magic-wormhole">Magic Wormhole</a>.
 */
public class WormholeCommand extends AbstractCommand {
    private static final Logger LOG = LoggerFactory.getLogger(WormholeCommand.class);
    private final Supplier<Scanner> scannerSupplier;
    private final ThrowingFunction<Triple<DrasylConfig, PrintStream, PrintStream>, SendingWormholeNode, DrasylException> sendingNodeSupplier;
    private final ThrowingFunction<Triple<DrasylConfig, PrintStream, PrintStream>, ReceivingWormholeNode, DrasylException> receivingNodeSupplier;
    private SendingWormholeNode sendingNode;
    private ReceivingWormholeNode receivingNode;

    public WormholeCommand() {
        this(
                System.out, // NOSONAR
                System.err, // NOSONAR
                () -> new Scanner(System.in), // NOSONAR
                triple -> new SendingWormholeNode(triple.first(), triple.second(), triple.third()),
                triple -> new ReceivingWormholeNode(triple.first(), triple.second(), triple.third()),
                null,
                null
        );
    }

    WormholeCommand(final PrintStream out,
                    final PrintStream err,
                    final Supplier<Scanner> scannerSupplier,
                    final ThrowingFunction<Triple<DrasylConfig, PrintStream, PrintStream>, SendingWormholeNode, DrasylException> sendingNodeSupplier,
                    final ThrowingFunction<Triple<DrasylConfig, PrintStream, PrintStream>, ReceivingWormholeNode, DrasylException> receivingNodeSupplier,
                    final SendingWormholeNode sendingNode,
                    final ReceivingWormholeNode receivingNode) {
        super(out, err);
        this.scannerSupplier = requireNonNull(scannerSupplier);
        this.sendingNodeSupplier = requireNonNull(sendingNodeSupplier);
        this.receivingNodeSupplier = requireNonNull(receivingNodeSupplier);
        this.sendingNode = sendingNode;
        this.receivingNode = receivingNode;
    }

    @Override
    public String getDescription() {
        return "Transfer a text message from one node to another, safely.";
    }

    @Override
    protected void help(final CommandLine cmd) {
        helpTemplate(
                "wormhole",
                "Transfer a text message from one node to another, safely.",
                "Use \"drasyl wormhole send\" to send a text message.",
                Map.of(
                        "send", "Send a text message.",
                        "receive", "Receive a text message (from \"drasyl wormhole send\")"
                )
        );
    }

    @Override
    protected void execute(final CommandLine cmd) {
        final List<String> argList = cmd.getArgList();
        if (argList.size() >= 2) { // NOSONAR
            final String subcommand = argList.get(1);
            switch (subcommand) {
                case "send":
                    send(cmd);
                    break;
                case "receive":
                    receive(cmd);
                    break;
                default:
                    err.println("ERR: Unknown command \"" + subcommand + "\" for \"drasyl wormhole\".");
            }
        }
        else {
            help(cmd);
        }
    }

    private void send(final CommandLine cmd) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (sendingNode != null) {
                LOG.info("Shutdown drasyl Node");
                sendingNode.shutdown().join();
            }
        }));

        try {
            // prepare node
            sendingNode = sendingNodeSupplier.apply(Triple.of(getDrasylConfig(cmd), out, err));
            sendingNode.start();

            // obtain text
            out.print("Text to send: ");
            final String text = scannerSupplier.get().nextLine();
            sendingNode.setText(text);

            // wait for node to finish
            sendingNode.doneFuture().get();
        }
        catch (final DrasylException | ExecutionException e) {
            throw new CliException(e);
        }
        catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void receive(final CommandLine cmd) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (receivingNode != null) {
                LOG.info("Shutdown drasyl Node");
                receivingNode.shutdown().join();
            }
        }));

        try {
            // prepare node
            receivingNode = receivingNodeSupplier.apply(Triple.of(getDrasylConfig(cmd), out, err));
            receivingNode.start();

            // obtain code
            final List<String> argList = cmd.getArgList();
            final String code;
            if (argList.size() < 3) { // NOSONAR
                out.print("Enter wormhole code: ");
                code = scannerSupplier.get().nextLine().strip();
            }
            else {
                code = argList.get(2).strip(); // NOSONAR
            }

            // request text
            if (code.length() < PUBLIC_KEY_LENGTH) {
                err.println("ERR: Invalid wormhole code supplied: must be at least 66 characters long.");
                return;
            }

            final CompressedPublicKey sender = CompressedPublicKey.of(code.substring(0, PUBLIC_KEY_LENGTH));
            final String password = code.substring(PUBLIC_KEY_LENGTH);
            receivingNode.requestText(sender, password);

            // wait for node to finish
            receivingNode.doneFuture().get();
        }
        catch (final DrasylException | ExecutionException | IllegalArgumentException e) {
            throw new CliException(e);
        }
        catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
