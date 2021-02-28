/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
/**
 * Copyright (c) 2004-2011 QOS.ch All rights reserved.
 * <p>
 * Permission is hereby granted, free  of charge, to any person obtaining a  copy  of this  software
 * and  associated  documentation files  (the "Software"), to  deal in  the Software without
 * restriction, including without limitation  the rights to  use, copy, modify,  merge, publish,
 * distribute,  sublicense, and/or sell  copies of  the Software,  and to permit persons to whom the
 * Software  is furnished to do so, subject to the following conditions:
 * <p>
 * The  above  copyright  notice  and  this permission  notice  shall  be included in all copies or
 * substantial portions of the Software.
 * <p>
 * THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND, EXPRESS OR  IMPLIED,
 * INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF MERCHANTABILITY,    FITNESS    FOR    A
 * PARTICULAR    PURPOSE    AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package org.drasyl.util.logging;

import io.netty.util.internal.logging.AbstractInternalLogger;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * <a href="https://java.sun.com/javase/6/docs/technotes/guides/logging/index.html">java.util.logging</a>
 * logger.
 */
@SuppressWarnings("java:S1312")
public class JdkLogger extends AbstractLogger {
    static final String SELF = JdkLogger.class.getName();
    static final String SUPER = AbstractInternalLogger.class.getName();
    private final Logger logger;

    public JdkLogger(final Logger logger) {
        super(logger.getName());
        this.logger = logger;
    }

    public Logger delegate() {
        return logger;
    }

    @Override
    public boolean isTraceEnabled() {
        return logger.isLoggable(Level.FINEST);
    }

    @Override
    public void trace(final String msg) {
        if (logger.isLoggable(Level.FINEST)) {
            log(Level.FINEST, msg, null);
        }
    }

    @Override
    public void trace(final String format, final Object arg) {
        if (logger.isLoggable(Level.FINEST)) {
            final FormattingTuple ft = MessageFormatter.format(format, arg);
            log(Level.FINEST, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void trace(final String format, final Object arg1, final Object arg2) {
        if (logger.isLoggable(Level.FINEST)) {
            final FormattingTuple ft = MessageFormatter.format(format, arg1, arg2);
            log(Level.FINEST, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void trace(final String format, final Object... arguments) {
        if (logger.isLoggable(Level.FINEST)) {
            final FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
            log(Level.FINEST, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void trace(final String msg, final Throwable t) {
        if (logger.isLoggable(Level.FINEST)) {
            log(Level.FINEST, msg, t);
        }
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isLoggable(Level.FINE);
    }

    @Override
    public void debug(final String msg) {
        if (logger.isLoggable(Level.FINE)) {
            log(Level.FINE, msg, null);
        }
    }

    @Override
    public void debug(final String format, final Object arg) {
        if (logger.isLoggable(Level.FINE)) {
            final FormattingTuple ft = MessageFormatter.format(format, arg);
            log(Level.FINE, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void debug(final String format, final Object arg1, final Object arg2) {
        if (logger.isLoggable(Level.FINE)) {
            final FormattingTuple ft = MessageFormatter.format(format, arg1, arg2);
            log(Level.FINE, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void debug(final String format, final Object... arguments) {
        if (logger.isLoggable(Level.FINE)) {
            final FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
            log(Level.FINE, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void debug(final String msg, final Throwable t) {
        if (logger.isLoggable(Level.FINE)) {
            log(Level.FINE, msg, t);
        }
    }

    @Override
    public boolean isInfoEnabled() {
        return logger.isLoggable(Level.INFO);
    }

    @Override
    public void info(final String msg) {
        if (logger.isLoggable(Level.INFO)) {
            log(Level.INFO, msg, null);
        }
    }

    @Override
    public void info(final String format, final Object arg) {
        if (logger.isLoggable(Level.INFO)) {
            final FormattingTuple ft = MessageFormatter.format(format, arg);
            log(Level.INFO, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void info(final String format, final Object arg1, final Object arg2) {
        if (logger.isLoggable(Level.INFO)) {
            final FormattingTuple ft = MessageFormatter.format(format, arg1, arg2);
            log(Level.INFO, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void info(final String format, final Object... arguments) {
        if (logger.isLoggable(Level.INFO)) {
            final FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
            log(Level.INFO, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void info(final String msg, final Throwable t) {
        if (logger.isLoggable(Level.INFO)) {
            log(Level.INFO, msg, t);
        }
    }

    @Override
    public boolean isWarnEnabled() {
        return logger.isLoggable(Level.WARNING);
    }

    @Override
    public void warn(final String msg) {
        if (logger.isLoggable(Level.WARNING)) {
            log(Level.WARNING, msg, null);
        }
    }

    @Override
    public void warn(final String format, final Object arg) {
        if (logger.isLoggable(Level.WARNING)) {
            final FormattingTuple ft = MessageFormatter.format(format, arg);
            log(Level.WARNING, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void warn(final String format, final Object arg1, final Object arg2) {
        if (logger.isLoggable(Level.WARNING)) {
            final FormattingTuple ft = MessageFormatter.format(format, arg1, arg2);
            log(Level.WARNING, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void warn(final String format, final Object... arguments) {
        if (logger.isLoggable(Level.WARNING)) {
            final FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
            log(Level.WARNING, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void warn(final String msg, final Throwable t) {
        if (logger.isLoggable(Level.WARNING)) {
            log(Level.WARNING, msg, t);
        }
    }

    @Override
    public boolean isErrorEnabled() {
        return logger.isLoggable(Level.SEVERE);
    }

    @Override
    public void error(final String msg) {
        if (logger.isLoggable(Level.SEVERE)) {
            log(Level.SEVERE, msg, null);
        }
    }

    @Override
    public void error(final String format, final Object arg) {
        if (logger.isLoggable(Level.SEVERE)) {
            final FormattingTuple ft = MessageFormatter.format(format, arg);
            log(Level.SEVERE, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void error(final String format, final Object arg1, final Object arg2) {
        if (logger.isLoggable(Level.SEVERE)) {
            final FormattingTuple ft = MessageFormatter.format(format, arg1, arg2);
            log(Level.SEVERE, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void error(final String format, final Object... arguments) {
        if (logger.isLoggable(Level.SEVERE)) {
            final FormattingTuple ft = MessageFormatter.arrayFormat(format, arguments);
            log(Level.SEVERE, ft.getMessage(), ft.getThrowable());
        }
    }

    @Override
    public void error(final String msg, final Throwable t) {
        if (logger.isLoggable(Level.SEVERE)) {
            log(Level.SEVERE, msg, t);
        }
    }

    /**
     * Log the message at the specified level with the specified throwable if any. This method
     * creates a LogRecord and fills in caller date before calling this instance's JDK14 logger.
     * <p>
     * See bug report #13 for more details.
     */
    private void log(final Level level,
                     final String msg,
                     final Throwable t) {
        // millis and thread are filled by the constructor
        final LogRecord record = new LogRecord(level, msg);
        record.setLoggerName(name());
        record.setThrown(t);
        fillCallerData(record);
        logger.log(record);
    }

    /**
     * Fill in caller data if possible.
     *
     * @param record The record to update
     */
    private static void fillCallerData(final LogRecord record) {
        final StackTraceElement[] steArray = new Throwable().getStackTrace();

        int selfIndex = -1;
        for (int i = 0; i < steArray.length; i++) {
            final String className = steArray[i].getClassName();
            if (className.equals(JdkLogger.SELF) || className.equals(SUPER)) {
                selfIndex = i;
                break;
            }
        }

        int found = -1;
        for (int i = selfIndex + 1; i < steArray.length; i++) {
            final String className = steArray[i].getClassName();
            if (!(className.equals(JdkLogger.SELF) || className.equals(SUPER))) {
                found = i;
                break;
            }
        }

        if (found != -1) {
            final StackTraceElement ste = steArray[found];
            // setting the class name has the side effect of setting
            // the needToInferCaller variable to false.
            record.setSourceClassName(ste.getClassName());
            record.setSourceMethodName(ste.getMethodName());
        }
    }
}
