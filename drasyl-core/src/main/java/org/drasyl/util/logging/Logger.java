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

import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public interface Logger {
    String name();

    boolean isTraceEnabled();

    void trace(String msg);

    void trace(String format, Object arg);

    void trace(String format, Supplier<Object> arg);

    void trace(String format, Object arg1, Object arg2);

    void trace(String format,
               Supplier<Object> arg1,
               Supplier<Object> arg2);

    void trace(String format, Object... arguments);

    void trace(String format, Supplier<Object>... arguments);

    void trace(String msg, Throwable t);

    boolean isDebugEnabled();

    void debug(String msg);

    void debug(String format, Object arg);

    void debug(String format, Supplier<Object> arg);

    void debug(String format, Object arg1, Object arg2);

    void debug(String format,
               Supplier<Object> arg1,
               Supplier<Object> arg2);

    void debug(String format, Object... arguments);

    void debug(String format, Supplier<Object>... arguments);

    void debug(String msg, Throwable t);

    boolean isInfoEnabled();

    void info(String msg);

    void info(String format, Object arg);

    void info(String format, Supplier<Object> arg);

    void info(String format, Object arg1, Object arg2);

    void info(String format,
              Supplier<Object> arg1,
              Supplier<Object> arg2);

    void info(String format, Object... arguments);

    void info(String format, Supplier<Object>... arguments);

    void info(String msg, Throwable t);

    boolean isWarnEnabled();

    void warn(String msg);

    void warn(String format, Object arg);

    void warn(String format, Supplier<Object> arg);

    void warn(String format, Object arg1, Object arg2);

    void warn(String format,
              Supplier<Object> arg1,
              Supplier<Object> arg2);

    void warn(String format, Object... arguments);

    void warn(String format, Supplier<Object>... arguments);

    void warn(String msg, Throwable t);

    boolean isErrorEnabled();

    void error(String msg);

    void error(String format, Object arg);

    void error(String format, Supplier<Object> arg);

    void error(String format, Object arg1, Object arg2);

    void error(String format,
               Supplier<Object> arg1,
               Supplier<Object> arg2);

    void error(String format, Object... arguments);

    void error(String format, Supplier<Object>... arguments);

    void error(String msg, Throwable t);
}
