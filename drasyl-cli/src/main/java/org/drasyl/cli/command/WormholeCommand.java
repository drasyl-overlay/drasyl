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
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.cli.CliException;
import org.drasyl.cli.command.wormhole.ReceivingWormholeNode;
import org.drasyl.cli.command.wormhole.SendingWormholeNode;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.util.ThrowingBiFunction;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.drasyl.identity.CompressedPublicKey.PUBLIC_KEY_LENGTH;

/**
 * Inspired by <a href="https://github.com/warner/magic-wormhole">Magic Wormhole</a>.
 */
public class WormholeCommand extends AbstractCommand {
    private final Supplier<Scanner> scannerSupplier;
    private final ThrowingBiFunction<DrasylConfig, PrintStream, SendingWormholeNode, DrasylException> sendingNodeSupplier;
    private final ThrowingBiFunction<DrasylConfig, PrintStream, ReceivingWormholeNode, DrasylException> receivingNodeSupplier;
    private final Consumer<Integer> exitSupplier;

    WormholeCommand(final PrintStream out,
                    final PrintStream err,
                    final Supplier<Scanner> scannerSupplier,
                    final ThrowingBiFunction<DrasylConfig, PrintStream, SendingWormholeNode, DrasylException> sendingNodeSupplier,
                    final ThrowingBiFunction<DrasylConfig, PrintStream, ReceivingWormholeNode, DrasylException> receivingNodeSupplier,
                    final Consumer<Integer> exitSupplier) {
        super(out, err);
        this.scannerSupplier = requireNonNull(scannerSupplier);
        this.sendingNodeSupplier = requireNonNull(sendingNodeSupplier);
        this.receivingNodeSupplier = requireNonNull(receivingNodeSupplier);
        this.exitSupplier = requireNonNull(exitSupplier);
    }

    WormholeCommand(final PrintStream out,
                    final Consumer<Integer> exitSupplier) {
        this(
                out,
                System.err, // NOSONAR
                () -> new Scanner(System.in), // NOSONAR
                SendingWormholeNode::new,
                ReceivingWormholeNode::new,
                exitSupplier
        );
    }

    public WormholeCommand() {
        this(System.out, System::exit); // NOSONAR
    }

    @Override
    public String getDescription() {
        return "Transfer a text message from one node to another, safely.";
    }

    @Override
    protected Options getOptions() {
        final Options options = super.getOptions();

        final Option client = Option.builder().longOpt("text").hasArg().argName("message").type(String.class).desc("Text message to send.").build();
        options.addOption(client);

        return options;
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
        SendingWormholeNode node = null;
        try {
            // prepare node
            node = sendingNodeSupplier.apply(getDrasylConfig(cmd), out);
            node.start();

            final String text;
            if (!cmd.hasOption("text")) {
                // obtain text
                out.print("Text to send: ");
                text = scannerSupplier.get().nextLine();
            }
            else {
                text = cmd.getParsedOptionValue("text").toString();
            }
            node.setText(text);

            // wait for node to finish
            node.doneFuture().get();
        }
        catch (final DrasylException e) {
            throw new CliException("Unable to create/run node", e);
        }
        catch (final ParseException e) {
            throw new CliException("Unable to parse options", e);
        }
        catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (final ExecutionException e) {
            throw new CliException(e);
        }
        finally {
            if (node != null) {
                node.shutdown().join();
            }

            exitSupplier.accept(0);
        }
    }

    private void receive(final CommandLine cmd) {
        ReceivingWormholeNode node = null;
        try {
            // prepare node
            node = receivingNodeSupplier.apply(getDrasylConfig(cmd), out);
            node.start();

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
            node.requestText(sender, password);

            // wait for node to finish
            node.doneFuture().get();
        }
        catch (final IllegalArgumentException e) {
            throw new CliException("Invalid wormhole code supplied supplied", e);
        }
        catch (final DrasylException e) {
            throw new CliException("Unable to create/run node", e);
        }
        catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (final ExecutionException e) {
            throw new CliException(e);
        }
        finally {
            if (node != null) {
                node.shutdown().join();
            }

            exitSupplier.accept(0);
        }
    }
}
