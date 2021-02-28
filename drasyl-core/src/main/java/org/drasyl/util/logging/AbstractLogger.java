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

import java.util.Arrays;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * A {@link Logger} that supports lazy evaluation of passend values.
 * <p>
 * Example:
 * <pre><code>
 * // without lazy logger
 * if (logger.isDebug()) {
 *     logger.debug("value: {}", this.expensiveComputation());
 * }
 *
 * // with lazy logger
 * logger.debug("value: {}", this::expensiveComputation);
 * </code></pre>
 */
abstract class AbstractLogger implements Logger {
    private final String name;

    protected AbstractLogger(final String name) {
        this.name = requireNonNull(name);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void trace(final String format, final Supplier<Object> arg) {
        if (isTraceEnabled()) {
            trace(format, arg.get());
        }
    }

    @Override
    public void trace(final String format,
                      final Supplier<Object> arg1,
                      final Supplier<Object> arg2) {
        if (isTraceEnabled()) {
            trace(format, arg1.get(), arg2.get());
        }
    }

    @Override
    @SafeVarargs
    public final void trace(final String format, final Supplier<Object>... arguments) {
        if (isTraceEnabled()) {
            trace(format, Arrays.stream(arguments).map(Supplier::get).toArray());
        }
    }

    @Override
    public void debug(final String format, final Supplier<Object> arg) {
        if (isDebugEnabled()) {
            debug(format, arg.get());
        }
    }

    @Override
    public void debug(final String format,
                      final Supplier<Object> arg1,
                      final Supplier<Object> arg2) {
        if (isDebugEnabled()) {
            debug(format, arg1.get(), arg2.get());
        }
    }

    @Override
    @SafeVarargs
    public final void debug(final String format, final Supplier<Object>... arguments) {
        if (isDebugEnabled()) {
            debug(format, Arrays.stream(arguments).map(Supplier::get).toArray());
        }
    }

    @Override
    public void info(final String format, final Supplier<Object> arg) {
        if (isInfoEnabled()) {
            info(format, arg.get());
        }
    }

    @Override
    public void info(final String format,
                     final Supplier<Object> arg1,
                     final Supplier<Object> arg2) {
        if (isInfoEnabled()) {
            info(format, arg1.get(), arg2.get());
        }
    }

    @Override
    @SafeVarargs
    public final void info(final String format, final Supplier<Object>... arguments) {
        if (isInfoEnabled()) {
            info(format, Arrays.stream(arguments).map(Supplier::get).toArray());
        }
    }

    @Override
    public void warn(final String format, final Supplier<Object> arg) {
        if (isWarnEnabled()) {
            warn(format, arg.get());
        }
    }

    @Override
    public void warn(final String format,
                     final Supplier<Object> arg1,
                     final Supplier<Object> arg2) {
        if (isWarnEnabled()) {
            warn(format, arg1.get(), arg2.get());
        }
    }

    @Override
    @SafeVarargs
    public final void warn(final String format, final Supplier<Object>... arguments) {
        if (isWarnEnabled()) {
            warn(format, Arrays.stream(arguments).map(Supplier::get).toArray());
        }
    }

    @Override
    public void error(final String format, final Supplier<Object> arg) {
        if (isErrorEnabled()) {
            error(format, arg.get());
        }
    }

    @Override
    public void error(final String format,
                      final Supplier<Object> arg1,
                      final Supplier<Object> arg2) {
        if (isErrorEnabled()) {
            error(format, arg1.get(), arg2.get());
        }
    }

    @Override
    @SafeVarargs
    public final void error(final String format, final Supplier<Object>... arguments) {
        if (isErrorEnabled()) {
            error(format, Arrays.stream(arguments).map(Supplier::get).toArray());
        }
    }
}
