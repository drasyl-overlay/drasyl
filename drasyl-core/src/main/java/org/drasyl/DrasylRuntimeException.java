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
package org.drasyl;

import java.util.Objects;

/**
 * All unchecked exceptions in drasyl inherit from this exception class.
 */
public class DrasylRuntimeException extends RuntimeException {
    public DrasylRuntimeException(Throwable cause) {
        super(cause);
    }

    public DrasylRuntimeException(String cause) {
        super(cause);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getCause(), getMessage());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DrasylRuntimeException that = (DrasylRuntimeException) o;
        return Objects.equals(getCause(), that.getCause()) &&
                Objects.equals(getMessage(), that.getMessage());
    }
}