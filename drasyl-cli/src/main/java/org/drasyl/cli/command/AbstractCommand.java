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
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.logging.Slf4JLogger;

import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

abstract class AbstractCommand implements Command {
    static final Path DEFAULT_CONF_PATH = Paths.get("drasyl.conf").toAbsolutePath();
    private static final String OPT_HELP = "help";
    private static final String OPT_VERBOSE = "verbose";
    private static final String OPT_CONFIG = "config";
    private static final Logger LOG = LoggerFactory.getLogger(AbstractCommand.class);
    private static final int LINE_WIDTH = 100;
    private static final int DESCRIPTION_PADDING = 6;
    protected final PrintStream out;
    protected final PrintStream err;

    protected AbstractCommand(
            final PrintStream out,
            final PrintStream err) {
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
    }

    @Override
    public void execute(final String[] args) {
        final Options flags = getOptions();
        final CommandLineParser parser = new DefaultParser();
        try {
            final CommandLine cmd = parser.parse(flags, args);

            setLogLevel(cmd);
            LOG.debug("drasyl: Version '{}' starting with parameters [{}]", DrasylNode::getVersion, () -> args.length > 0 ? ("'" + String.join("', '", args) + "'") : "");

            if (cmd.hasOption(OPT_HELP)) {
                help(cmd);
            }
            else {
                execute(cmd);
            }
        }
        catch (final ParseException e) {
            err.println("ERR: Unable to parse args.");
            e.printStackTrace(err);
        }
    }

    @SuppressWarnings("SameParameterValue")
    protected void helpTemplate(final String name, final String header, final String footer) {
        helpTemplate(name, header, footer, Map.of());
    }

    @SuppressWarnings({ "java:S1192" })
    protected void helpTemplate(final String name,
                                final String header,
                                final String footer,
                                final Map<String, String> commands) {
        if (!header.isEmpty()) {
            out.println(header);
            out.println();
        }

        out.println("Usage:");
        out.println("  drasyl" + (!name.isEmpty() ? (" " + name) : "") + " [flags]");
        if (!commands.isEmpty()) {
            out.println("  drasyl" + (!name.isEmpty() ? (" " + name) : "") + " [command]");
        }
        out.println();

        if (!commands.isEmpty()) {
            out.println("Available Commands:");
            for (final Map.Entry<String, String> entry : commands.entrySet()) {
                final String command = entry.getKey();
                final String description = entry.getValue();
                out.printf("%-15s", command);
                out.printf("%-15s%n", description);
            }
            out.println();
        }

        out.println("Flags:");
        final HelpFormatter formatter = new HelpFormatter();
        final PrintWriter printWriter = new PrintWriter(out, false, UTF_8);
        formatter.printOptions(printWriter, LINE_WIDTH, getOptions(), 0, DESCRIPTION_PADDING);
        printWriter.flush();

        if (!footer.isEmpty()) {
            out.println();
            out.println(footer);
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

    @SuppressWarnings({ "unused" })
    protected abstract void help(CommandLine cmd);

    protected abstract void execute(CommandLine cmd);

    @SuppressWarnings("java:S1312")
    protected void setLogLevel(final CommandLine cmd) {
        if (cmd.hasOption(OPT_VERBOSE)) {
            final String levelString = cmd.getOptionValue(OPT_VERBOSE);

            final Logger logger = LoggerFactory.getLogger("org.drasyl");
            if (logger instanceof Slf4JLogger && ((Slf4JLogger) logger).delegate() instanceof ch.qos.logback.classic.Logger) {
                final Level level = Level.valueOf(levelString);
                ((ch.qos.logback.classic.Logger) ((Slf4JLogger) logger).delegate()).setLevel(level);
            }
        }
    }

    protected DrasylConfig getDrasylConfig(final CommandLine cmd) {
        final DrasylConfig config;
        if (cmd.hasOption(OPT_CONFIG)) {
            final File file = new File(cmd.getOptionValue(OPT_CONFIG));
            LOG.info("Using config file from '{}'", file);
            config = DrasylConfig.parseFile(file);
        }
        else if (DEFAULT_CONF_PATH.toFile().exists()) {
            LOG.info("Using config file from '{}'", DEFAULT_CONF_PATH);
            config = DrasylConfig.parseFile(DEFAULT_CONF_PATH.toFile());
        }
        else {
            LOG.info("Config file '{}' not found - using defaults", DEFAULT_CONF_PATH);
            config = new DrasylConfig();
        }

        return config;
    }
}
