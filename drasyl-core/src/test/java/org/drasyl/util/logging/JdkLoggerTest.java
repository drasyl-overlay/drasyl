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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static java.util.logging.Level.WARNING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdkLoggerTest {
    @Mock
    private java.util.logging.Logger logger;
    private JdkLogger underTest;

    @BeforeEach
    void setUp() {
        when(logger.getName()).thenReturn("underlying logger");
        underTest = spy(new JdkLogger(logger));
    }

    @Test
    void delegate() {
        assertEquals(logger, underTest.delegate());
    }

    @Test
    void name() {
        assertEquals("underlying logger", underTest.name());
    }

    @Nested
    class Eager {
        @Nested
        class Trace {
            private final Level level = FINEST;

            @BeforeEach
            void setUp() {
                when(logger.isLoggable(level)).thenReturn(true);
            }

            @Test
            void isEnabled() {
                assertTrue(underTest.isTraceEnabled());
            }

            @Test
            void format() {
                underTest.trace("format");

                verify(logger).log(argThatRecord(new LogRecord(level, "format")));
            }

            @Test
            void formatObject(@Mock final Object arg) {
                underTest.trace("format {}", arg);

                verify(logger).log(argThatRecord(new LogRecord(level, "format " + arg.toString())));
            }

            @Test
            void formatObjectObject(@Mock final Object arg1, @Mock final Object arg2) {
                underTest.trace("format {} {}", arg1, arg2);

                verify(logger).log(argThatRecord(new LogRecord(level, "format " + arg1.toString() + " " + arg2.toString())));
            }

            @Test
            void formatArguments(@Mock final Object arg1,
                                 @Mock final Object arg2,
                                 @Mock final Object arg3) {
                underTest.trace("format {} {} {}", arg1, arg2, arg3);

                verify(logger).log(argThatRecord(new LogRecord(level, "format " + arg1.toString() + " " + arg2.toString() + " " + arg3.toString())));
            }

            @Test
            void formatThrowable(@Mock final Throwable t) {
                underTest.trace("format", t);

                verify(logger).log(argThatRecord(new LogRecord(level, "format")));
            }
        }

        @Nested
        class Debug {
            private final Level level = FINE;

            @BeforeEach
            void setUp() {
                when(logger.isLoggable(level)).thenReturn(true);
            }

            @Test
            void isEnabled() {
                assertTrue(underTest.isDebugEnabled());
            }

            @Test
            void format() {
                underTest.debug("format");

                verify(logger).log(argThatRecord(new LogRecord(level, "format")));
            }

            @Test
            void formatObject(@Mock final Object arg) {
                underTest.debug("format {}", arg);

                verify(logger).log(argThatRecord(new LogRecord(level, "format " + arg.toString())));
            }

            @Test
            void formatObjectObject(@Mock final Object arg1, @Mock final Object arg2) {
                underTest.debug("format {} {}", arg1, arg2);

                verify(logger).log(argThatRecord(new LogRecord(level, "format " + arg1.toString() + " " + arg2.toString())));
            }

            @Test
            void formatArguments(@Mock final Object arg1,
                                 @Mock final Object arg2,
                                 @Mock final Object arg3) {
                underTest.debug("format {} {} {}", arg1, arg2, arg3);

                verify(logger).log(argThatRecord(new LogRecord(level, "format " + arg1.toString() + " " + arg2.toString() + " " + arg3.toString())));
            }

            @Test
            void formatThrowable(@Mock final Throwable t) {
                underTest.debug("format", t);

                verify(logger).log(argThatRecord(new LogRecord(level, "format")));
            }
        }

        @Nested
        class Info {
            private final Level level = INFO;

            @BeforeEach
            void setUp() {
                when(logger.isLoggable(level)).thenReturn(true);
            }

            @Test
            void isEnabled() {
                assertTrue(underTest.isInfoEnabled());
            }

            @Test
            void format() {
                underTest.info("format");

                verify(logger).log(argThatRecord(new LogRecord(level, "format")));
            }

            @Test
            void formatObject(@Mock final Object arg) {
                underTest.info("format {}", arg);

                verify(logger).log(argThatRecord(new LogRecord(level, "format " + arg.toString())));
            }

            @Test
            void formatObjectObject(@Mock final Object arg1, @Mock final Object arg2) {
                underTest.info("format {} {}", arg1, arg2);

                verify(logger).log(argThatRecord(new LogRecord(level, "format " + arg1.toString() + " " + arg2.toString())));
            }

            @Test
            void formatArguments(@Mock final Object arg1,
                                 @Mock final Object arg2,
                                 @Mock final Object arg3) {
                underTest.info("format {} {} {}", arg1, arg2, arg3);

                verify(logger).log(argThatRecord(new LogRecord(level, "format " + arg1.toString() + " " + arg2.toString() + " " + arg3.toString())));
            }

            @Test
            void formatThrowable(@Mock final Throwable t) {
                underTest.info("format", t);

                verify(logger).log(argThatRecord(new LogRecord(level, "format")));
            }
        }

        @Nested
        class Warn {
            private final Level level = WARNING;

            @BeforeEach
            void setUp() {
                when(logger.isLoggable(level)).thenReturn(true);
            }

            @Test
            void isEnabled() {
                assertTrue(underTest.isWarnEnabled());
            }

            @Test
            void format() {
                underTest.warn("format");

                verify(logger).log(argThatRecord(new LogRecord(level, "format")));
            }

            @Test
            void formatObject(@Mock final Object arg) {
                underTest.warn("format {}", arg);

                verify(logger).log(argThatRecord(new LogRecord(level, "format " + arg.toString())));
            }

            @Test
            void formatObjectObject(@Mock final Object arg1, @Mock final Object arg2) {
                underTest.warn("format {} {}", arg1, arg2);

                verify(logger).log(argThatRecord(new LogRecord(level, "format " + arg1.toString() + " " + arg2.toString())));
            }

            @Test
            void formatArguments(@Mock final Object arg1,
                                 @Mock final Object arg2,
                                 @Mock final Object arg3) {
                underTest.warn("format {} {} {}", arg1, arg2, arg3);

                verify(logger).log(argThatRecord(new LogRecord(level, "format " + arg1.toString() + " " + arg2.toString() + " " + arg3.toString())));
            }

            @Test
            void formatThrowable(@Mock final Throwable t) {
                underTest.warn("format", t);

                verify(logger).log(argThatRecord(new LogRecord(level, "format")));
            }
        }

        @Nested
        class Error {
            private final Level level = SEVERE;

            @BeforeEach
            void setUp() {
                when(logger.isLoggable(level)).thenReturn(true);
            }

            @Test
            void isEnabled() {
                assertTrue(underTest.isErrorEnabled());
            }

            @Test
            void format() {
                underTest.error("format");

                verify(logger).log(argThatRecord(new LogRecord(level, "format")));
            }

            @Test
            void formatObject(@Mock final Object arg) {
                underTest.error("format {}", arg);

                verify(logger).log(argThatRecord(new LogRecord(level, "format " + arg.toString())));
            }

            @Test
            void formatObjectObject(@Mock final Object arg1, @Mock final Object arg2) {
                underTest.error("format {} {}", arg1, arg2);

                verify(logger).log(argThatRecord(new LogRecord(level, "format " + arg1.toString() + " " + arg2.toString())));
            }

            @Test
            void formatArguments(@Mock final Object arg1,
                                 @Mock final Object arg2,
                                 @Mock final Object arg3) {
                underTest.error("format {} {} {}", arg1, arg2, arg3);

                verify(logger).log(argThatRecord(new LogRecord(level, "format " + arg1.toString() + " " + arg2.toString() + " " + arg3.toString())));
            }

            @Test
            void formatThrowable(@Mock final Throwable t) {
                underTest.error("format", t);

                verify(logger).log(argThatRecord(new LogRecord(level, "format")));
            }
        }
    }

    @Nested
    class Lazy {
        @Nested
        class Trace {
            @Nested
            class WhenEnabled {
                @BeforeEach
                void setUp() {
                    when(logger.isLoggable(FINEST)).thenReturn(true);
                }

                @Test
                void formatObject(@Mock final Supplier<Object> arg) {
                    underTest.trace("format", arg);

                    verify(arg).get();
                }

                @Test
                void formatObjectObject(@Mock final Supplier<Object> arg1,
                                        @Mock final Supplier<Object> arg2) {
                    underTest.trace("format", arg1, arg2);

                    verify(arg1).get();
                    verify(arg2).get();
                }

                @Test
                void formatArguments(@Mock final Supplier<Object> arg1,
                                     @Mock final Supplier<Object> arg2,
                                     @Mock final Supplier<Object> arg3) {
                    underTest.trace("format", arg1, arg2, arg3);

                    verify(arg1).get();
                    verify(arg2).get();
                    verify(arg3).get();
                }
            }

            @Nested
            class WhenDisabled {
                @BeforeEach
                void setUp() {
                    when(logger.isLoggable(FINEST)).thenReturn(false);
                }

                @Test
                void formatObject(@Mock final Supplier<Object> arg) {
                    underTest.trace("format", arg);

                    verify(arg, never()).get();
                }

                @Test
                void formatObjectObject(@Mock final Supplier<Object> arg1,
                                        @Mock final Supplier<Object> arg2) {
                    underTest.trace("format", arg1, arg2);

                    verify(arg1, never()).get();
                    verify(arg2, never()).get();
                }

                @Test
                void formatArguments(@Mock final Supplier<Object> arg1,
                                     @Mock final Supplier<Object> arg2,
                                     @Mock final Supplier<Object> arg3) {
                    underTest.trace("format", arg1, arg2, arg3);

                    verify(arg1, never()).get();
                    verify(arg2, never()).get();
                    verify(arg3, never()).get();
                }
            }
        }

        @Nested
        class Debug {
            @Nested
            class WhenEnabled {
                @BeforeEach
                void setUp() {
                    when(logger.isLoggable(FINE)).thenReturn(true);
                }

                @Test
                void formatObject(@Mock final Supplier<Object> arg) {
                    underTest.debug("format", arg);

                    verify(arg).get();
                }

                @Test
                void formatObjectObject(@Mock final Supplier<Object> arg1,
                                        @Mock final Supplier<Object> arg2) {
                    underTest.debug("format", arg1, arg2);

                    verify(arg1).get();
                    verify(arg2).get();
                }

                @Test
                void formatArguments(@Mock final Supplier<Object> arg1,
                                     @Mock final Supplier<Object> arg2,
                                     @Mock final Supplier<Object> arg3) {
                    underTest.debug("format", arg1, arg2, arg3);

                    verify(arg1).get();
                    verify(arg2).get();
                    verify(arg3).get();
                }
            }

            @Nested
            class WhenDisabled {
                @BeforeEach
                void setUp() {
                    when(logger.isLoggable(FINE)).thenReturn(false);
                }

                @Test
                void formatObject(@Mock final Supplier<Object> arg) {
                    underTest.debug("format", arg);

                    verify(arg, never()).get();
                }

                @Test
                void formatObjectObject(@Mock final Supplier<Object> arg1,
                                        @Mock final Supplier<Object> arg2) {
                    underTest.debug("format", arg1, arg2);

                    verify(arg1, never()).get();
                    verify(arg2, never()).get();
                }

                @Test
                void formatArguments(@Mock final Supplier<Object> arg1,
                                     @Mock final Supplier<Object> arg2,
                                     @Mock final Supplier<Object> arg3) {
                    underTest.debug("format", arg1, arg2, arg3);

                    verify(arg1, never()).get();
                    verify(arg2, never()).get();
                    verify(arg3, never()).get();
                }
            }
        }

        @Nested
        class Info {
            @Nested
            class WhenEnabled {
                @BeforeEach
                void setUp() {
                    when(logger.isLoggable(INFO)).thenReturn(true);
                }

                @Test
                void formatObject(@Mock final Supplier<Object> arg) {
                    underTest.info("format", arg);

                    verify(arg).get();
                }

                @Test
                void formatObjectObject(@Mock final Supplier<Object> arg1,
                                        @Mock final Supplier<Object> arg2) {
                    underTest.info("format", arg1, arg2);

                    verify(arg1).get();
                    verify(arg2).get();
                }

                @Test
                void formatArguments(@Mock final Supplier<Object> arg1,
                                     @Mock final Supplier<Object> arg2,
                                     @Mock final Supplier<Object> arg3) {
                    underTest.info("format", arg1, arg2, arg3);

                    verify(arg1).get();
                    verify(arg2).get();
                    verify(arg3).get();
                }
            }

            @Nested
            class WhenDisabled {
                @BeforeEach
                void setUp() {
                    when(logger.isLoggable(INFO)).thenReturn(false);
                }

                @Test
                void formatObject(@Mock final Supplier<Object> arg) {
                    underTest.info("format", arg);

                    verify(arg, never()).get();
                }

                @Test
                void formatObjectObject(@Mock final Supplier<Object> arg1,
                                        @Mock final Supplier<Object> arg2) {
                    underTest.info("format", arg1, arg2);

                    verify(arg1, never()).get();
                    verify(arg2, never()).get();
                }

                @Test
                void formatArguments(@Mock final Supplier<Object> arg1,
                                     @Mock final Supplier<Object> arg2,
                                     @Mock final Supplier<Object> arg3) {
                    underTest.info("format", arg1, arg2, arg3);

                    verify(arg1, never()).get();
                    verify(arg2, never()).get();
                    verify(arg3, never()).get();
                }
            }
        }

        @Nested
        class Warn {
            @Nested
            class WhenEnabled {
                @BeforeEach
                void setUp() {
                    when(logger.isLoggable(WARNING)).thenReturn(true);
                }

                @Test
                void formatObject(@Mock final Supplier<Object> arg) {
                    underTest.warn("format", arg);

                    verify(arg).get();
                }

                @Test
                void formatObjectObject(@Mock final Supplier<Object> arg1,
                                        @Mock final Supplier<Object> arg2) {
                    underTest.warn("format", arg1, arg2);

                    verify(arg1).get();
                    verify(arg2).get();
                }

                @Test
                void formatArguments(@Mock final Supplier<Object> arg1,
                                     @Mock final Supplier<Object> arg2,
                                     @Mock final Supplier<Object> arg3) {
                    underTest.warn("format", arg1, arg2, arg3);

                    verify(arg1).get();
                    verify(arg2).get();
                    verify(arg3).get();
                }
            }

            @Nested
            class WhenDisabled {
                @BeforeEach
                void setUp() {
                    when(logger.isLoggable(WARNING)).thenReturn(false);
                }

                @Test
                void formatObject(@Mock final Supplier<Object> arg) {
                    underTest.warn("format", arg);

                    verify(arg, never()).get();
                }

                @Test
                void formatObjectObject(@Mock final Supplier<Object> arg1,
                                        @Mock final Supplier<Object> arg2) {
                    underTest.warn("format", arg1, arg2);

                    verify(arg1, never()).get();
                    verify(arg2, never()).get();
                }

                @Test
                void formatArguments(@Mock final Supplier<Object> arg1,
                                     @Mock final Supplier<Object> arg2,
                                     @Mock final Supplier<Object> arg3) {
                    underTest.warn("format", arg1, arg2, arg3);

                    verify(arg1, never()).get();
                    verify(arg2, never()).get();
                    verify(arg3, never()).get();
                }
            }
        }

        @Nested
        class Error {
            @Nested
            class WhenEnabled {
                @BeforeEach
                void setUp() {
                    when(logger.isLoggable(SEVERE)).thenReturn(true);
                }

                @Test
                void formatObject(@Mock final Supplier<Object> arg) {
                    underTest.error("format", arg);

                    verify(arg).get();
                }

                @Test
                void formatObjectObject(@Mock final Supplier<Object> arg1,
                                        @Mock final Supplier<Object> arg2) {
                    underTest.error("format", arg1, arg2);

                    verify(arg1).get();
                    verify(arg2).get();
                }

                @Test
                void formatArguments(@Mock final Supplier<Object> arg1,
                                     @Mock final Supplier<Object> arg2,
                                     @Mock final Supplier<Object> arg3) {
                    underTest.error("format", arg1, arg2, arg3);

                    verify(arg1).get();
                    verify(arg2).get();
                    verify(arg3).get();
                }
            }

            @Nested
            class WhenDisabled {
                @BeforeEach
                void setUp() {
                    when(logger.isLoggable(SEVERE)).thenReturn(false);
                }

                @Test
                void formatObject(@Mock final Supplier<Object> arg) {
                    underTest.error("format", arg);

                    verify(arg, never()).get();
                }

                @Test
                void formatObjectObject(@Mock final Supplier<Object> arg1,
                                        @Mock final Supplier<Object> arg2) {
                    underTest.error("format", arg1, arg2);

                    verify(arg1, never()).get();
                    verify(arg2, never()).get();
                }

                @Test
                void formatArguments(@Mock final Supplier<Object> arg1,
                                     @Mock final Supplier<Object> arg2,
                                     @Mock final Supplier<Object> arg3) {
                    underTest.error("format", arg1, arg2, arg3);

                    verify(arg1, never()).get();
                    verify(arg2, never()).get();
                    verify(arg3, never()).get();
                }
            }
        }
    }

    private LogRecord argThatRecord(final LogRecord record) {
        return argThat(other -> record.getLevel().equals(other.getLevel()) && record.getMessage().equals(other.getMessage()));
    }
}
