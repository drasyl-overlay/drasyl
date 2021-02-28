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
 * Logger factory which creates a
 * <a href="https://docs.oracle.com/javase/7/docs/technotes/guides/logging/">java.util.logging</a>
 * logger.
 *
 * @see JdkLogger
 */
public final class JdkLoggerFactory extends LoggerFactory {
    public static final LoggerFactory INSTANCE = new JdkLoggerFactory();

    private JdkLoggerFactory() {
        // singleton
    }

    @Override
    protected Logger newLogger(final String name) {
        return new JdkLogger(java.util.logging.Logger.getLogger(name));
    }
}
