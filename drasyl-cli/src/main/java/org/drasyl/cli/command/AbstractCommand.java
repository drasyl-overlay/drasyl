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

import ch.qos.logback.classic.Level;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylNode;
import org.drasyl.cli.CliException;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

abstract class AbstractCommand implements Command {
    static final Path DEFAULT_CONF_PATH = Paths.get("drasyl.conf").toAbsolutePath();
    private static final String OPT_HELP = "help";
    private static final String OPT_VERBOSE = "verbose";
    private static final String OPT_CONFIG = "config";
    private static final Logger log = LoggerFactory.getLogger(AbstractCommand.class);
    protected final PrintStream printStream;

    protected AbstractCommand(
            final PrintStream printStream) {
        this.printStream = printStream;
    }

    @Override
    public void execute(final String[] args) throws CliException {
        final Options flags = getOptions();
        final CommandLineParser parser = new DefaultParser();
        try {
            final CommandLine cmd = parser.parse(flags, args);

            setLogLevel(cmd);
            log.debug("drasyl: Version '{}' starting with parameters [{}]", DrasylNode.getVersion(), args.length > 0 ? "'" + String.join("', '", args) + "'" : "");

            if (cmd.hasOption(OPT_HELP)) {
                help(cmd);
            }
            else {
                execute(cmd);
            }
        }
        catch (final ParseException e) {
            throw new CliException(e);
        }
    }

    protected void helpTemplate(final String name, final String header, final String footer) {
        helpTemplate(name, header, footer, Map.of());
    }

    @SuppressWarnings({ "java:S1192" })
    protected void helpTemplate(final String name,
                                final String header,
                                final String footer,
                                final Map<String, String> commands) {
        if (!header.isEmpty()) {
            printStream.println(header);
            printStream.println();
        }

        printStream.println("Usage:");
        printStream.println("  drasyl" + (!name.isEmpty() ? " " + name : "") + " [flags]");
        if (!commands.isEmpty()) {
            printStream.println("  drasyl" + (!name.isEmpty() ? " " + name : "") + " [command]");
        }
        printStream.println();

        if (!commands.isEmpty()) {
            printStream.println("Available Commands:");
            for (final Map.Entry<String, String> entry : commands.entrySet()) {
                final String command = entry.getKey();
                final String description = entry.getValue();
                printStream.printf("%-15s", command);
                printStream.printf("%-15s%n", description);
            }
            printStream.println();
        }

        printStream.println("Flags:");
        final HelpFormatter formatter = new HelpFormatter();
        final PrintWriter printWriter = new PrintWriter(printStream);
        formatter.printOptions(printWriter, 100, getOptions(), 0, 6);
        printWriter.flush();

        if (!footer.isEmpty()) {
            printStream.println();
            printStream.println(footer);
        }
    }

    protected void helpTemplate(final String name, final String header) {
        helpTemplate(name, header, "");
    }

    protected Options getOptions() {
        final Options options = new Options();

        // global flags
        final Option help = Option.builder("h").longOpt(OPT_HELP).desc("Show this help.").build();
        options.addOption(help);

        final Option verbose = Option.builder("v").longOpt(OPT_VERBOSE).hasArg().argName("level").desc("Sets the log level (off, error, warn, info, debug, trace; default: warn)").build();
        options.addOption(verbose);

        final Option config = Option.builder("c").longOpt(OPT_CONFIG).hasArg().argName("file").desc("Load configuration from specified file (default: " + DEFAULT_CONF_PATH + ").").build();
        options.addOption(config);

        return options;
    }

    protected abstract void help(CommandLine cmd) throws CliException;

    protected abstract void execute(CommandLine cmd) throws CliException;

    protected void setLogLevel(final CommandLine cmd) {
        if (cmd.hasOption(OPT_VERBOSE)) {
            final String levelString = cmd.getOptionValue(OPT_VERBOSE);
            final Level level = Level.valueOf(levelString);

            final ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.drasyl").delegate();
            root.setLevel(level);
        }
    }

    protected DrasylConfig getDrasylConfig(final CommandLine cmd) {
        final DrasylConfig config;
        if (cmd.hasOption(OPT_CONFIG)) {
            final File file = new File(cmd.getOptionValue(OPT_CONFIG));
            log.info("Using config file from '{}'", file);
            config = DrasylConfig.parseFile(file);
        }
        else if (DEFAULT_CONF_PATH.toFile().exists()) {
            log.info("Using config file from '{}'", DEFAULT_CONF_PATH);
            config = DrasylConfig.parseFile(DEFAULT_CONF_PATH.toFile());
        }
        else {
            log.info("Config file '{}' not found - using defaults", DEFAULT_CONF_PATH);
            config = new DrasylConfig();
        }

        return config;
    }
}