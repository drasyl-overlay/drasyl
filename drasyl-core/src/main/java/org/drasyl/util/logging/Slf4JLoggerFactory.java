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

import org.slf4j.helpers.NOPLoggerFactory;

/**
 * Logger factory which creates a <a href="https://www.slf4j.org/">SLF4J</a> logger.
 *
 * @see Slf4JLogger
 */
public final class Slf4JLoggerFactory extends LoggerFactory {
    public Slf4JLoggerFactory() {
        if (org.slf4j.LoggerFactory.getILoggerFactory() instanceof NOPLoggerFactory) {
            throw new NoClassDefFoundError("NOPLoggerFactory not supported");
        }
    }

    @Override
    protected Logger newLogger(final String name) {
        return new Slf4JLogger(org.slf4j.LoggerFactory.getLogger(name));
    }
}
