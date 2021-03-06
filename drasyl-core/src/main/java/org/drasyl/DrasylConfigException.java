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
package org.drasyl;

/**
 * An {@link Exception} which is thrown by {@link DrasylConfig}.
 */
public class DrasylConfigException extends IllegalArgumentException {
    public DrasylConfigException(final Throwable cause) {
        super(cause);
    }

    public DrasylConfigException(final String path, final Throwable cause) {
        super("Invalid value at '" + path + "'", cause);
    }

    public DrasylConfigException(final String path, final String message) {
        super("Invalid value at '" + path + "': " + message);
    }
}
