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
package org.drasyl.util.logging;

import org.slf4j.Marker;

import java.util.Arrays;
import java.util.function.Supplier;

public class Logger {
    private final org.slf4j.Logger delegate;

    Logger(final org.slf4j.Logger delegate) {
        this.delegate = delegate;
    }

    public org.slf4j.Logger delegate() {
        return delegate;
    }

    public String getName() {
        return delegate.getName();
    }

    public boolean isTraceEnabled() {
        return delegate.isTraceEnabled();
    }

    public void trace(final String msg) {
        delegate.trace(msg);
    }

    public void trace(final String format, final Object arg) {
        delegate.trace(format, arg);
    }

    public void trace(final String format, final Supplier<Object> arg) {
        if (isTraceEnabled()) {
            trace(format, arg.get());
        }
    }

    public void trace(final String format, final Object arg1, final Object arg2) {
        delegate.trace(format, arg1, arg2);
    }

    public void trace(final String format,
                      final Supplier<Object> arg1,
                      final Supplier<Object> arg2) {
        if (isTraceEnabled()) {
            trace(format, arg1.get(), arg2.get());
        }
    }

    public void trace(final String format, final Object... arguments) {
        delegate.trace(format, arguments);
    }

    @SafeVarargs
    public final void trace(final String format, final Supplier<Object>... arguments) {
        if (isTraceEnabled()) {
            trace(format, Arrays.stream(arguments).map(Supplier::get).toArray());
        }
    }

    public void trace(final String msg, final Throwable t) {
        delegate.trace(msg, t);
    }

    public boolean isTraceEnabled(final Marker marker) {
        return delegate.isTraceEnabled(marker);
    }

    public void trace(final Marker marker, final String msg) {
        delegate.trace(marker, msg);
    }

    public void trace(final Marker marker, final String format, final Object arg) {
        delegate.trace(marker, format, arg);
    }

    public void trace(final Marker marker, final String format, final Supplier<Object> arg) {
        if (isTraceEnabled(marker)) {
            trace(marker, format, arg.get());
        }
    }

    public void trace(final Marker marker,
                      final String format,
                      final Object arg1,
                      final Object arg2) {
        delegate.trace(marker, format, arg1, arg2);
    }

    public void trace(final Marker marker,
                      final String format,
                      final Supplier<Object> arg1,
                      final Supplier<Object> arg2) {
        if (isTraceEnabled(marker)) {
            trace(marker, format, arg1.get(), arg2.get());
        }
    }

    public void trace(final Marker marker, final String format, final Object... argArray) {
        delegate.trace(marker, format, argArray);
    }

    @SafeVarargs
    public final void trace(final Marker marker,
                            final String format,
                            final Supplier<Object>... argArray) {
        if (isTraceEnabled(marker)) {
            trace(marker, format, Arrays.stream(argArray).map(Supplier::get).toArray());
        }
    }

    public void trace(final Marker marker, final String msg, final Throwable t) {
        delegate.trace(marker, msg, t);
    }

    public boolean isDebugEnabled() {
        return delegate.isDebugEnabled();
    }

    public void debug(final String msg) {
        delegate.debug(msg);
    }

    public void debug(final String format, final Object arg) {
        delegate.debug(format, arg);
    }

    public void debug(final String format, final Supplier<Object> arg) {
        if (isDebugEnabled()) {
            debug(format, arg.get());
        }
    }

    public void debug(final String format, final Object arg1, final Object arg2) {
        delegate.debug(format, arg1, arg2);
    }

    public void debug(final String format,
                      final Supplier<Object> arg1,
                      final Supplier<Object> arg2) {
        if (isDebugEnabled()) {
            debug(format, arg1.get(), arg2.get());
        }
    }

    public void debug(final String format, final Object... arguments) {
        delegate.debug(format, arguments);
    }

    @SafeVarargs
    public final void debug(final String format, final Supplier<Object>... arguments) {
        if (isDebugEnabled()) {
            debug(format, Arrays.stream(arguments).map(Supplier::get).toArray());
        }
    }

    public void debug(final String msg, final Throwable t) {
        delegate.debug(msg, t);
    }

    public boolean isDebugEnabled(final Marker marker) {
        return delegate.isDebugEnabled(marker);
    }

    public void debug(final Marker marker, final String msg) {
        delegate.debug(marker, msg);
    }

    public void debug(final Marker marker, final String format, final Object arg) {
        delegate.debug(marker, format, arg);
    }

    public void debug(final Marker marker, final String format, final Supplier<Object> arg) {
        if (isDebugEnabled(marker)) {
            debug(marker, format, arg.get());
        }
    }

    public void debug(final Marker marker,
                      final String format,
                      final Object arg1,
                      final Object arg2) {
        delegate.debug(marker, format, arg1, arg2);
    }

    public void debug(final Marker marker,
                      final String format,
                      final Supplier<Object> arg1,
                      final Supplier<Object> arg2) {
        if (isDebugEnabled(marker)) {
            debug(marker, format, arg1.get(), arg2.get());
        }
    }

    public void debug(final Marker marker, final String format, final Object... arguments) {
        delegate.debug(marker, format, arguments);
    }

    @SafeVarargs
    public final void debug(final Marker marker,
                            final String format,
                            final Supplier<Object>... arguments) {
        if (isDebugEnabled(marker)) {
            debug(marker, format, Arrays.stream(arguments).map(Supplier::get).toArray());
        }
    }

    public void debug(final Marker marker, final String msg, final Throwable t) {
        delegate.debug(marker, msg, t);
    }

    public boolean isInfoEnabled() {
        return delegate.isInfoEnabled();
    }

    public void info(final String msg) {
        delegate.info(msg);
    }

    public void info(final String format, final Object arg) {
        delegate.info(format, arg);
    }

    public void info(final String format, final Supplier<Object> arg) {
        if (isInfoEnabled()) {
            info(format, arg.get());
        }
    }

    public void info(final String format, final Object arg1, final Object arg2) {
        delegate.info(format, arg1, arg2);
    }

    public void info(final String format,
                     final Supplier<Object> arg1,
                     final Supplier<Object> arg2) {
        if (isInfoEnabled()) {
            info(format, arg1.get(), arg2.get());
        }
    }

    public void info(final String format, final Object... arguments) {
        delegate.info(format, arguments);
    }

    @SafeVarargs
    public final void info(final String format, final Supplier<Object>... arguments) {
        if (isInfoEnabled()) {
            info(format, Arrays.stream(arguments).map(Supplier::get).toArray());
        }
    }

    public void info(final String msg, final Throwable t) {
        delegate.info(msg, t);
    }

    public boolean isInfoEnabled(final Marker marker) {
        return delegate.isInfoEnabled(marker);
    }

    public void info(final Marker marker, final String msg) {
        delegate.info(marker, msg);
    }

    public void info(final Marker marker, final String format, final Object arg) {
        delegate.info(marker, format, arg);
    }

    public void info(final Marker marker, final String format, final Supplier<Object> arg) {
        if (isInfoEnabled(marker)) {
            info(marker, format, arg.get());
        }
    }

    public void info(final Marker marker,
                     final String format,
                     final Object arg1,
                     final Object arg2) {
        delegate.info(marker, format, arg1, arg2);
    }

    public void info(final Marker marker,
                     final String format,
                     final Supplier<Object> arg1,
                     final Supplier<Object> arg2) {
        if (isInfoEnabled(marker)) {
            info(marker, format, arg1.get(), arg2.get());
        }
    }

    public void info(final Marker marker, final String format, final Object... arguments) {
        delegate.info(marker, format, arguments);
    }

    @SafeVarargs
    public final void info(final Marker marker,
                           final String format,
                           final Supplier<Object>... argArray) {
        if (isInfoEnabled(marker)) {
            info(marker, format, Arrays.stream(argArray).map(Supplier::get).toArray());
        }
    }

    public void info(final Marker marker, final String msg, final Throwable t) {
        delegate.info(marker, msg, t);
    }

    public boolean isWarnEnabled() {
        return delegate.isWarnEnabled();
    }

    public void warn(final String msg) {
        delegate.warn(msg);
    }

    public void warn(final String format, final Object arg) {
        delegate.warn(format, arg);
    }

    public void warn(final String format, final Supplier<Object> arg) {
        if (isWarnEnabled()) {
            warn(format, arg.get());
        }
    }

    public void warn(final String format, final Object arg1, final Object arg2) {
        delegate.warn(format, arg1, arg2);
    }

    public void warn(final String format,
                     final Supplier<Object> arg1,
                     final Supplier<Object> arg2) {
        if (isWarnEnabled()) {
            warn(format, arg1.get(), arg2.get());
        }
    }

    public void warn(final String format, final Object... arguments) {
        delegate.warn(format, arguments);
    }

    @SafeVarargs
    public final void warn(final String format, final Supplier<Object>... arguments) {
        if (isWarnEnabled()) {
            warn(format, Arrays.stream(arguments).map(Supplier::get).toArray());
        }
    }

    public void warn(final String msg, final Throwable t) {
        delegate.warn(msg, t);
    }

    public boolean isWarnEnabled(final Marker marker) {
        return delegate.isWarnEnabled(marker);
    }

    public void warn(final Marker marker, final String msg) {
        delegate.warn(marker, msg);
    }

    public void warn(final Marker marker, final String format, final Object arg) {
        delegate.warn(marker, format, arg);
    }

    public void warn(final Marker marker, final String format, final Supplier<Object> arg) {
        if (isWarnEnabled(marker)) {
            warn(marker, format, arg.get());
        }
    }

    public void warn(final Marker marker,
                     final String format,
                     final Object arg1,
                     final Object arg2) {
        delegate.warn(marker, format, arg1, arg2);
    }

    public void warn(final Marker marker,
                     final String format,
                     final Supplier<Object> arg1,
                     final Supplier<Object> arg2) {
        if (isWarnEnabled(marker)) {
            warn(marker, format, arg1.get(), arg2.get());
        }
    }

    public void warn(final Marker marker, final String format, final Object... arguments) {
        delegate.warn(marker, format, arguments);
    }

    @SafeVarargs
    public final void warn(final Marker marker,
                           final String format,
                           final Supplier<Object>... argArray) {
        if (isWarnEnabled(marker)) {
            warn(marker, format, Arrays.stream(argArray).map(Supplier::get).toArray());
        }
    }

    public void warn(final Marker marker, final String msg, final Throwable t) {
        delegate.warn(marker, msg, t);
    }

    public boolean isErrorEnabled() {
        return delegate.isErrorEnabled();
    }

    public void error(final String msg) {
        delegate.error(msg);
    }

    public void error(final String format, final Object arg) {
        delegate.error(format, arg);
    }

    public void error(final String format, final Supplier<Object> arg) {
        if (isErrorEnabled()) {
            error(format, arg.get());
        }
    }

    public void error(final String format, final Object arg1, final Object arg2) {
        delegate.error(format, arg1, arg2);
    }

    public void error(final String format,
                      final Supplier<Object> arg1,
                      final Supplier<Object> arg2) {
        if (isErrorEnabled()) {
            error(format, arg1.get(), arg2.get());
        }
    }

    public void error(final String format, final Object... arguments) {
        delegate.error(format, arguments);
    }

    @SafeVarargs
    public final void error(final String format, final Supplier<Object>... arguments) {
        if (isErrorEnabled()) {
            error(format, Arrays.stream(arguments).map(Supplier::get).toArray());
        }
    }

    public void error(final String msg, final Throwable t) {
        delegate.error(msg, t);
    }

    public boolean isErrorEnabled(final Marker marker) {
        return delegate.isErrorEnabled(marker);
    }

    public void error(final Marker marker, final String msg) {
        delegate.error(marker, msg);
    }

    public void error(final Marker marker, final String format, final Object arg) {
        delegate.error(marker, format, arg);
    }

    public void error(final Marker marker, final String format, final Supplier<Object> arg) {
        if (isErrorEnabled(marker)) {
            error(marker, format, arg.get());
        }
    }

    public void error(final Marker marker,
                      final String format,
                      final Object arg1,
                      final Object arg2) {
        delegate.error(marker, format, arg1, arg2);
    }

    public void error(final Marker marker,
                      final String format,
                      final Supplier<Object> arg1,
                      final Supplier<Object> arg2) {
        if (isErrorEnabled(marker)) {
            error(marker, format, arg1.get(), arg2.get());
        }
    }

    public void error(final Marker marker, final String format, final Object... arguments) {
        delegate.error(marker, format, arguments);
    }

    @SafeVarargs
    public final void error(final Marker marker,
                            final String format,
                            final Supplier<Object>... argArray) {
        if (isErrorEnabled(marker)) {
            error(marker, format, Arrays.stream(argArray).map(Supplier::get).toArray());
        }
    }

    public void error(final Marker marker, final String msg, final Throwable t) {
        delegate.error(marker, msg, t);
    }
}
