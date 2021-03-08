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
    /**
     * Return the name of this {@code Logger} instance.
     *
     * @return name of this logger instance
     */
    String name();

    /**
     * Is the logger instance enabled for the TRACE level?
     *
     * @return {@code true} if this Logger is enabled for the TRACE level, {@code false} otherwise.
     */
    boolean isTraceEnabled();

    /**
     * Log a message at the TRACE level.
     *
     * @param msg the message string to be logged
     */
    void trace(String msg);

    /**
     * Log a message at the TRACE level according to the specified format and argument.
     *
     * @param format the format string
     * @param arg    the argument
     */
    void trace(String format, Object arg);

    /**
     * Log a message at the TRACE level according to the specified format and argument supplier.
     * <p/>
     * <p>The supplier is not called when the logger is disabled for the TRACE level.</p>
     *
     * @param format   the format string
     * @param supplier the argument supplier
     */
    void trace(String format, Supplier<Object> supplier);

    /**
     * Log a message at the TRACE level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     */
    void trace(String format, Object arg1, Object arg2);

    /**
     * Log a message at the TRACE level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the TRACE level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     */
    void trace(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2);

    /**
     * Log a message at the TRACE level according to the specified format and arguments.
     *
     * @param format    the format string
     * @param arguments a list of 3 or more arguments
     */
    void trace(String format, Object... arguments);

    /**
     * Log a message at the TRACE level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the TRACE level.</p>
     *
     * @param format    the format string
     * @param suppliers a list of 3 or more argument suppliers
     */
    void trace(String format, Supplier<Object>... suppliers);

    /**
     * Log an exception (throwable) at the TRACE level with an accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t   the exception (throwable) to log
     */
    void trace(String msg, Throwable t);

    /**
     * Is the logger instance enabled for the DEBUG level?
     *
     * @return {@code true} if this Logger is enabled for the DEBUG level, {@code false} otherwise.
     */
    boolean isDebugEnabled();

    /**
     * Log a message at the DEBUG level.
     *
     * @param msg the message string to be logged
     */
    void debug(String msg);

    /**
     * Log a message at the DEBUG level according to the specified format and argument.
     *
     * @param format the format string
     * @param arg    the argument
     */
    void debug(String format, Object arg);

    /**
     * Log a message at the DEBUG level according to the specified format and argument supplier.
     * <p/>
     * <p>The supplier is not called when the logger is disabled for the DEBUG level.</p>
     *
     * @param format   the format string
     * @param supplier the argument supplier
     */
    void debug(String format, Supplier<Object> supplier);

    /**
     * Log a message at the DEBUG level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     */
    void debug(String format, Object arg1, Object arg2);

    /**
     * Log a message at the DEBUG level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the DEBUG level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     */
    void debug(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2);

    /**
     * Log a message at the DEBUG level according to the specified format and arguments.
     *
     * @param format    the format string
     * @param arguments a list of 3 or more arguments
     */
    void debug(String format, Object... arguments);

    /**
     * Log a message at the DEBUG level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the DEBUG level.</p>
     *
     * @param format    the format string
     * @param suppliers a list of 3 or more argument suppliers
     */
    void debug(String format, Supplier<Object>... suppliers);

    /**
     * Log an exception (throwable) at the DEBUG level with an accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t   the exception (throwable) to log
     */
    void debug(String msg, Throwable t);

    /**
     * Is the logger instance enabled for the INFO level?
     *
     * @return {@code true} if this Logger is enabled for the INFO level, {@code false} otherwise.
     */
    boolean isInfoEnabled();

    /**
     * Log a message at the INFO level.
     *
     * @param msg the message string to be logged
     */
    void info(String msg);

    /**
     * Log a message at the INFO level according to the specified format and argument.
     *
     * @param format the format string
     * @param arg    the argument
     */
    void info(String format, Object arg);

    /**
     * Log a message at the INFO level according to the specified format and argument supplier.
     * <p/>
     * <p>The supplier is not called when the logger is disabled for the INFO level.</p>
     *
     * @param format   the format string
     * @param supplier the argument supplier
     */
    void info(String format, Supplier<Object> supplier);

    /**
     * Log a message at the INFO level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     */
    void info(String format, Object arg1, Object arg2);

    /**
     * Log a message at the INFO level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the INFO level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     */
    void info(String format,
              Supplier<Object> supplier1,
              Supplier<Object> supplier2);

    /**
     * Log a message at the INFO level according to the specified format and arguments.
     *
     * @param format    the format string
     * @param arguments a list of 3 or more arguments
     */
    void info(String format, Object... arguments);

    /**
     * Log a message at the INFO level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the INFO level.</p>
     *
     * @param format    the format string
     * @param suppliers a list of 3 or more argument suppliers
     */
    void info(String format, Supplier<Object>... suppliers);

    /**
     * Log an exception (throwable) at the INFO level with an accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t   the exception (throwable) to log
     */
    void info(String msg, Throwable t);

    /**
     * Is the logger instance enabled for the WARN level?
     *
     * @return {@code true} if this Logger is enabled for the WARN level, {@code false} otherwise.
     */
    boolean isWarnEnabled();

    /**
     * Log a message at the WARN level.
     *
     * @param msg the message string to be logged
     */
    void warn(String msg);

    /**
     * Log a message at the WARN level according to the specified format and argument.
     *
     * @param format the format string
     * @param arg    the argument
     */
    void warn(String format, Object arg);

    /**
     * Log a message at the WARN level according to the specified format and argument supplier.
     * <p/>
     * <p>The supplier is not called when the logger is disabled for the WARN level.</p>
     *
     * @param format   the format string
     * @param supplier the argument supplier
     */
    void warn(String format, Supplier<Object> supplier);

    /**
     * Log a message at the WARN level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     */
    void warn(String format, Object arg1, Object arg2);

    /**
     * Log a message at the WARN level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the WARN level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     */
    void warn(String format,
              Supplier<Object> supplier1,
              Supplier<Object> supplier2);

    /**
     * Log a message at the WARN level according to the specified format and arguments.
     *
     * @param format    the format string
     * @param arguments a list of 3 or more arguments
     */
    void warn(String format, Object... arguments);

    /**
     * Log a message at the WARN level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the WARN level.</p>
     *
     * @param format    the format string
     * @param suppliers a list of 3 or more argument suppliers
     */
    void warn(String format, Supplier<Object>... suppliers);

    /**
     * Log an exception (throwable) at the WARN level with an accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t   the exception (throwable) to log
     */
    void warn(String msg, Throwable t);

    /**
     * Is the logger instance enabled for the ERROR level?
     *
     * @return {@code true} if this Logger is enabled for the ERROR level, {@code false} otherwise.
     */
    boolean isErrorEnabled();

    /**
     * Log a message at the ERROR level.
     *
     * @param msg the message string to be logged
     */
    void error(String msg);

    /**
     * Log a message at the ERROR level according to the specified format and argument.
     *
     * @param format the format string
     * @param arg    the argument
     */
    void error(String format, Object arg);

    /**
     * Log a message at the ERROR level according to the specified format and argument supplier.
     * <p/>
     * <p>The supplier is not called when the logger is disabled for the ERROR level.</p>
     *
     * @param format   the format string
     * @param supplier the argument supplier
     */
    void error(String format, Supplier<Object> supplier);

    /**
     * Log a message at the ERROR level according to the specified format and arguments.
     *
     * @param format the format string
     * @param arg1   the first argument
     * @param arg2   the second argument
     */
    void error(String format, Object arg1, Object arg2);

    /**
     * Log a message at the ERROR level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the ERROR level.</p>
     *
     * @param format    the format string
     * @param supplier1 the first argument supplier
     * @param supplier2 the second argument supplier
     */
    void error(String format,
               Supplier<Object> supplier1,
               Supplier<Object> supplier2);

    /**
     * Log a message at the ERROR level according to the specified format and arguments.
     *
     * @param format    the format string
     * @param arguments a list of 3 or more arguments
     */
    void error(String format, Object... arguments);

    /**
     * Log a message at the ERROR level according to the specified format and argument suppliers.
     * <p/>
     * <p>The suppliers are not called when the logger is disabled for the ERROR level.</p>
     *
     * @param format    the format string
     * @param suppliers a list of 3 or more argument suppliers
     */
    void error(String format, Supplier<Object>... suppliers);

    /**
     * Log an exception (throwable) at the ERROR level with an accompanying message.
     *
     * @param msg the message accompanying the exception
     * @param t   the exception (throwable) to log
     */
    void error(String msg, Throwable t);
}
