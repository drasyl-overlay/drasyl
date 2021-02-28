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
package org.drasyl.util.logging;

/**
 * Provides loggers. If available, <a href="https://www.slf4j.org/">SLF4J</a> loggers are used.
 * Otherwise, <a href="https://java.sun.com/javase/6/docs/technotes/guides/logging/index.html">java.util.logging</a>
 * loggers are used..
 */
@SuppressWarnings("java:S118")
public abstract class LoggerFactory {
    private static LoggerFactory defaultFactory;

    /**
     * Creates a new logger with name of specified {@code clazz}.
     */
    public static Logger getLogger(final Class<?> clazz) {
        return getLogger(clazz.getName());
    }

    /**
     * Creates a new logger with specified {@code name}.
     */
    public static Logger getLogger(final String name) {
        return getDefaultFactory().newLogger(name);
    }

    protected abstract Logger newLogger(final String name);

    private static LoggerFactory getDefaultFactory() {
        if (defaultFactory == null) {
            defaultFactory = getSlf4JLoggerFactory();

            if (defaultFactory == null) {
                // fallback to jdk logging
                defaultFactory = getJdkLoggerFactory();
            }
        }
        return defaultFactory;
    }

    @SuppressWarnings("java:S1166")
    private static LoggerFactory getSlf4JLoggerFactory() {
        try {
            final LoggerFactory factory = new Slf4JLoggerFactory();
            factory.newLogger(LoggerFactory.class.getName()).debug("Using SLF4J as the default logging framework");
            return factory;
        }
        catch (final LinkageError ignore) {
            return null;
        }
    }

    private static LoggerFactory getJdkLoggerFactory() {
        final LoggerFactory factory = JdkLoggerFactory.INSTANCE;
        factory.newLogger(LoggerFactory.class.getName()).debug("Using java.util.logging as the default logging framework");
        return factory;
    }
}
