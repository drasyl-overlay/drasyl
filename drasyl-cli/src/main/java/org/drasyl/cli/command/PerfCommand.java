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
import org.drasyl.cli.command.perf.PerfClientNode;
import org.drasyl.cli.command.perf.PerfServerNode;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.util.ThrowingBiFunction;

import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Inspired by <a href="https://iperf.fr/iperf-download.php">https://iperf.fr/iperf-download.php</a>.
 */
@SuppressWarnings("java:S1192")
public class PerfCommand extends AbstractCommand {
    private static final int DEFAULT_MPS = 100;
    private static final int DEFAULT_TIME = 10;
    private static final int DEFAULT_SIZE = 850;
    private final ThrowingBiFunction<DrasylConfig, PrintStream, PerfServerNode, DrasylException> serverNodeSupplier;
    private final ThrowingBiFunction<DrasylConfig, PrintStream, PerfClientNode, DrasylException> clientNodeSupplier;
    private final Consumer<Integer> exitSupplier;

    public PerfCommand() {
        this(
                System.out, // NOSONAR
                System.err, // NOSONAR
                PerfServerNode::new,
                PerfClientNode::new,
                System::exit
        );
    }

    PerfCommand(final PrintStream out,
                final PrintStream err,
                final ThrowingBiFunction<DrasylConfig, PrintStream, PerfServerNode, DrasylException> serverNodeSupplier,
                final ThrowingBiFunction<DrasylConfig, PrintStream, PerfClientNode, DrasylException> clientNodeSupplier,
                final Consumer<Integer> exitSupplier) {
        super(out, err);
        this.serverNodeSupplier = requireNonNull(serverNodeSupplier);
        this.clientNodeSupplier = requireNonNull(clientNodeSupplier);
        this.exitSupplier = requireNonNull(exitSupplier);
    }

    @Override
    public String getDescription() {
        return "Speed test tool.";
    }

    @Override
    protected Options getOptions() {
        final Options options = super.getOptions();

        final Option client = Option.builder("c").longOpt("client").hasArg().argName("publicKey").type(String.class).desc("Runs in client mode, connecting to <publicKey>. If not set, runs in server mode.").build();
        options.addOption(client);

        final Option mps = Option.builder().longOpt("mps").hasArg().argName("count").type(Number.class).desc("Client: Messages per second (default: " + DEFAULT_MPS + ")").build();
        options.addOption(mps);

        final Option time = Option.builder().longOpt("time").hasArg().argName("seconds").type(Number.class).desc("Client: Time in seconds to transmit for (default: " + DEFAULT_TIME + ")").build();
        options.addOption(time);

        final Option size = Option.builder().longOpt("size").hasArg().argName("bytes").type(Number.class).desc("Client: Messages size (default: " + DEFAULT_SIZE + ")").build();
        options.addOption(size);

        final Option direct = Option.builder().longOpt("direct").desc("Client: Wait for direct connection before performing the test").build();
        options.addOption(direct);

        final Option reverse = Option.builder().longOpt("reverse").desc("Client: Run in reverse mode (server sends, client receives)").build();
        options.addOption(reverse);

        return options;
    }

    @Override
    protected void help(final CommandLine cmd) {
        helpTemplate(
                "perf",
                "Tool for measuring network performance. A specified number of messages per second of the desired\n" +
                        "size are sent to another node over a defined period of time. The amount of transferred data, the\n" +
                        "bitrate, as well as the number of lost and (out of order) delivered messages, are recorded.",
                "",
                Map.of()
        );
    }

    @Override
    protected void execute(final CommandLine cmd) {
        if (cmd.hasOption("client")) {
            client(cmd);
        }
        else {
            server(cmd);
        }
    }

    private void server(final CommandLine cmd) {
        PerfServerNode node = null;
        try {
            // prepare node
            node = serverNodeSupplier.apply(getDrasylConfig(cmd), out);
            node.start();

            // wait for node to finish
            node.doneFuture().get();
        }
        catch (final DrasylException e) {
            throw new CliException("Unable to create/run perf server node", e);
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

    private void client(final CommandLine cmd) {
        PerfClientNode node = null;
        try {
            // prepare node
            node = clientNodeSupplier.apply(getDrasylConfig(cmd), out);
            node.start();

            // obtain server address and test options
            final Object value = cmd.getParsedOptionValue("client");
            final CompressedPublicKey server = CompressedPublicKey.of(value.toString());

            final int time;
            if (cmd.getParsedOptionValue("time") != null) {
                time = ((Number) cmd.getParsedOptionValue("time")).intValue();
            }
            else {
                time = DEFAULT_TIME;
            }

            final int messagesPerSecond;
            if (cmd.getParsedOptionValue("mps") != null) {
                messagesPerSecond = ((Number) cmd.getParsedOptionValue("mps")).intValue();
            }
            else {
                messagesPerSecond = DEFAULT_MPS;
            }

            final int size;
            if (cmd.getParsedOptionValue("size") != null) {
                size = ((Number) cmd.getParsedOptionValue("size")).intValue();
            }
            else {
                size = DEFAULT_SIZE;
            }

            if (time < 1 || messagesPerSecond < 1 || size < 1) {
                throw new CliException("time, mps, and size must be greater than 0");
            }

            final boolean directConnection = cmd.hasOption("direct");
            final boolean reverse = cmd.hasOption("reverse");

            node.setTestOptions(server, time, messagesPerSecond, size, directConnection, reverse);

            // wait for node to finish
            node.doneFuture().get();
        }
        catch (final IllegalArgumentException e) {
            throw new CliException("Invalid server address supplied", e);
        }
        catch (final DrasylException e) {
            throw new CliException("Unable to create/run perf client", e);
        }
        catch (final ParseException e) {
            throw new CliException("Unable parse options", e);
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
