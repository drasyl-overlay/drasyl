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

import org.slf4j.Logger;

/**
 * <a href="https://www.slf4j.org/">SLF4J</a> logger.
 */
public class Slf4JLogger extends AbstractLogger {
    @SuppressWarnings("java:S1312")
    private final Logger logger;

    Slf4JLogger(final Logger logger) {
        super(logger.getName());
        this.logger = logger;
    }

    public Logger delegate() {
        return logger;
    }

    @Override
    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    @Override
    public void trace(final String msg) {
        logger.trace(msg);
    }

    @Override
    public void trace(final String format, final Object arg) {
        logger.trace(format, arg);
    }

    @Override
    public void trace(final String format, final Object arg1, final Object arg2) {
        logger.trace(format, arg1, arg2);
    }

    @Override
    public void trace(final String format, final Object... arguments) {
        logger.trace(format, arguments);
    }

    @Override
    public void trace(final String msg, final Throwable t) {
        logger.trace(msg, t);
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public void debug(final String msg) {
        logger.debug(msg);
    }

    @Override
    public void debug(final String format, final Object arg) {
        logger.debug(format, arg);
    }

    @Override
    public void debug(final String format, final Object arg1, final Object arg2) {
        logger.debug(format, arg1, arg2);
    }

    @Override
    public void debug(final String format, final Object... arguments) {
        logger.debug(format, arguments);
    }

    @Override
    public void debug(final String msg, final Throwable t) {
        logger.debug(msg, t);
    }

    @Override
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    @Override
    public void info(final String msg) {
        logger.info(msg);
    }

    @Override
    public void info(final String format, final Object arg) {
        logger.info(format, arg);
    }

    @Override
    public void info(final String format, final Object arg1, final Object arg2) {
        logger.info(format, arg1, arg2);
    }

    @Override
    public void info(final String format, final Object... arguments) {
        logger.info(format, arguments);
    }

    @Override
    public void info(final String msg, final Throwable t) {
        logger.info(msg, t);
    }

    @Override
    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }

    @Override
    public void warn(final String msg) {
        logger.warn(msg);
    }

    @Override
    public void warn(final String format, final Object arg) {
        logger.warn(format, arg);
    }

    @Override
    public void warn(final String format, final Object arg1, final Object arg2) {
        logger.warn(format, arg1, arg2);
    }

    @Override
    public void warn(final String format, final Object... arguments) {
        logger.warn(format, arguments);
    }

    @Override
    public void warn(final String msg, final Throwable t) {
        logger.warn(msg, t);
    }

    @Override
    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }

    @Override
    public void error(final String msg) {
        logger.error(msg);
    }

    @Override
    public void error(final String format, final Object arg) {
        logger.error(format, arg);
    }

    @Override
    public void error(final String format, final Object arg1, final Object arg2) {
        logger.error(format, arg1, arg2);
    }

    @Override
    public void error(final String format, final Object... arguments) {
        logger.error(format, arguments);
    }

    @Override
    public void error(final String msg, final Throwable t) {
        logger.error(msg, t);
    }
}
