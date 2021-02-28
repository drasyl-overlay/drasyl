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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Slf4JLoggerTest {
    @Mock
    private org.slf4j.Logger logger;
    private Slf4JLogger underTest;

    @BeforeEach
    void setUp() {
        when(logger.getName()).thenReturn("underlying logger");
        underTest = spy(new Slf4JLogger(logger));
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
            @Test
            void isEnabled() {
                when(logger.isTraceEnabled()).thenReturn(true);

                assertTrue(underTest.isTraceEnabled());
            }

            @Test
            void format() {
                underTest.trace("format");

                verify(logger).trace("format");
            }

            @Test
            void formatObject(@Mock final Object arg) {
                underTest.trace("format {}", arg);

                verify(logger).trace("format {}", arg);
            }

            @Test
            void formatObjectObject(@Mock final Object arg1, @Mock final Object arg2) {
                underTest.trace("format {} {}", arg1, arg2);

                verify(logger).trace("format {} {}", arg1, arg2);
            }

            @Test
            void formatArguments(@Mock final Object arg1,
                                 @Mock final Object arg2,
                                 @Mock final Object arg3) {
                underTest.trace("format {} {} {}", arg1, arg2, arg3);

                verify(logger).trace("format {} {} {}", arg1, arg2, arg3);
            }

            @Test
            void formatThrowable(@Mock final Throwable t) {
                underTest.trace("format", t);

                verify(logger).trace("format", t);
            }
        }

        @Nested
        class Debug {
            @Test
            void isEnabled() {
                when(logger.isDebugEnabled()).thenReturn(true);

                assertTrue(underTest.isDebugEnabled());
            }

            @Test
            void format() {
                underTest.debug("format");

                verify(logger).debug("format");
            }

            @Test
            void formatObject(@Mock final Object arg) {
                underTest.debug("format {}", arg);

                verify(logger).debug("format {}", arg);
            }

            @Test
            void formatObjectObject(@Mock final Object arg1, @Mock final Object arg2) {
                underTest.debug("format {} {}", arg1, arg2);

                verify(logger).debug("format {} {}", arg1, arg2);
            }

            @Test
            void formatArguments(@Mock final Object arg1,
                                 @Mock final Object arg2,
                                 @Mock final Object arg3) {
                underTest.debug("format {} {} {}", arg1, arg2, arg3);

                verify(logger).debug("format {} {} {}", arg1, arg2, arg3);
            }

            @Test
            void formatThrowable(@Mock final Throwable t) {
                underTest.debug("format", t);

                verify(logger).debug("format", t);
            }
        }

        @Nested
        class Info {
            @Test
            void isEnabled() {
                when(logger.isInfoEnabled()).thenReturn(true);

                assertTrue(underTest.isInfoEnabled());
            }

            @Test
            void format() {
                underTest.info("format");

                verify(logger).info("format");
            }

            @Test
            void formatObject(@Mock final Object arg) {
                underTest.info("format {}", arg);

                verify(logger).info("format {}", arg);
            }

            @Test
            void formatObjectObject(@Mock final Object arg1, @Mock final Object arg2) {
                underTest.info("format {} {}", arg1, arg2);

                verify(logger).info("format {} {}", arg1, arg2);
            }

            @Test
            void formatArguments(@Mock final Object arg1,
                                 @Mock final Object arg2,
                                 @Mock final Object arg3) {
                underTest.info("format {} {} {}", arg1, arg2, arg3);

                verify(logger).info("format {} {} {}", arg1, arg2, arg3);
            }

            @Test
            void formatThrowable(@Mock final Throwable t) {
                underTest.info("format", t);

                verify(logger).info("format", t);
            }
        }

        @Nested
        class Warn {
            @Test
            void isEnabled() {
                when(logger.isWarnEnabled()).thenReturn(true);

                assertTrue(underTest.isWarnEnabled());
            }

            @Test
            void format() {
                underTest.warn("format");

                verify(logger).warn("format");
            }

            @Test
            void formatObject(@Mock final Object arg) {
                underTest.warn("format {}", arg);

                verify(logger).warn("format {}", arg);
            }

            @Test
            void formatObjectObject(@Mock final Object arg1, @Mock final Object arg2) {
                underTest.warn("format {} {}", arg1, arg2);

                verify(logger).warn("format {} {}", arg1, arg2);
            }

            @Test
            void formatArguments(@Mock final Object arg1,
                                 @Mock final Object arg2,
                                 @Mock final Object arg3) {
                underTest.warn("format {} {} {}", arg1, arg2, arg3);

                verify(logger).warn("format {} {} {}", arg1, arg2, arg3);
            }

            @Test
            void formatThrowable(@Mock final Throwable t) {
                underTest.warn("format", t);

                verify(logger).warn("format", t);
            }
        }

        @Nested
        class Error {
            @Test
            void isEnabled() {
                when(logger.isErrorEnabled()).thenReturn(true);

                assertTrue(underTest.isErrorEnabled());
            }

            @Test
            void format() {
                underTest.error("format");

                verify(logger).error("format");
            }

            @Test
            void formatObject(@Mock final Object arg) {
                underTest.error("format {}", arg);

                verify(logger).error("format {}", arg);
            }

            @Test
            void formatObjectObject(@Mock final Object arg1, @Mock final Object arg2) {
                underTest.error("format {} {}", arg1, arg2);

                verify(logger).error("format {} {}", arg1, arg2);
            }

            @Test
            void formatArguments(@Mock final Object arg1,
                                 @Mock final Object arg2,
                                 @Mock final Object arg3) {
                underTest.error("format {} {} {}", arg1, arg2, arg3);

                verify(logger).error("format {} {} {}", arg1, arg2, arg3);
            }

            @Test
            void formatThrowable(@Mock final Throwable t) {
                underTest.error("format", t);

                verify(logger).error("format", t);
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
                    when(logger.isTraceEnabled()).thenReturn(true);
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
                    when(logger.isTraceEnabled()).thenReturn(false);
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
                    when(logger.isDebugEnabled()).thenReturn(true);
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
                    when(logger.isDebugEnabled()).thenReturn(false);
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
                    when(logger.isInfoEnabled()).thenReturn(true);
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
                    when(logger.isInfoEnabled()).thenReturn(false);
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
                    when(logger.isWarnEnabled()).thenReturn(true);
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
                    when(logger.isWarnEnabled()).thenReturn(false);
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
                    when(logger.isErrorEnabled()).thenReturn(true);
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
                    when(logger.isErrorEnabled()).thenReturn(false);
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
}
