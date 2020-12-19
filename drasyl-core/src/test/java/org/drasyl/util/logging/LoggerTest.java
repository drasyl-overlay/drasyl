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
package org.drasyl.util.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Marker;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoggerTest {
    @Mock
    private org.slf4j.Logger delegate;
    private Logger underTest;

    @BeforeEach
    void setUp() {
        underTest = spy(new Logger(delegate));
    }

    @Test
    void delegate() {
        assertEquals(delegate, underTest.delegate());
    }

    @Test
    void getName() {
        when(delegate.getName()).thenReturn("underlying logger");

        assertEquals("underlying logger", underTest.getName());
    }

    @Nested
    class Eager {
        @Nested
        class Trace {
            @Test
            void isEnabled() {
                when(delegate.isTraceEnabled()).thenReturn(true);

                assertTrue(underTest.isTraceEnabled());
            }

            @Test
            void format() {
                underTest.trace("format");

                verify(delegate).trace("format");
            }

            @Test
            void formatObject(@Mock final Object arg) {
                underTest.trace("format", arg);

                verify(delegate).trace("format", arg);
            }

            @Test
            void formatObjectObject(@Mock final Object arg1, @Mock final Object arg2) {
                underTest.trace("format", arg1, arg2);

                verify(delegate).trace("format", arg1, arg2);
            }

            @Test
            void formatArguments(@Mock final Object arg1,
                                 @Mock final Object arg2,
                                 @Mock final Object arg3) {
                underTest.trace("format", arg1, arg2, arg3);

                verify(delegate).trace("format", arg1, arg2, arg3);
            }

            @Test
            void formatThrowable(@Mock final Throwable t) {
                underTest.trace("format", t);

                verify(delegate).trace("format", t);
            }
        }

        @Nested
        class TraceMarker {
            @Mock
            private Marker marker;

            @Test
            void isEnabled() {
                when(delegate.isTraceEnabled(marker)).thenReturn(true);

                assertTrue(underTest.isTraceEnabled(marker));
            }

            @Test
            void format() {
                underTest.trace(marker, "format");

                verify(delegate).trace(marker, "format");
            }

            @Test
            void formatObject(@Mock final Object arg) {
                underTest.trace(marker, "format", arg);

                verify(delegate).trace(marker, "format", arg);
            }

            @Test
            void formatObjectObject(@Mock final Object arg1, @Mock final Object arg2) {
                underTest.trace(marker, "format", arg1, arg2);

                verify(delegate).trace(marker, "format", arg1, arg2);
            }

            @Test
            void formatArguments(@Mock final Object arg1,
                                 @Mock final Object arg2,
                                 @Mock final Object arg3) {
                underTest.trace(marker, "format", arg1, arg2, arg3);

                verify(delegate).trace(marker, "format", arg1, arg2, arg3);
            }

            @Test
            void formatThrowable(@Mock final Throwable t) {
                underTest.trace(marker, "format", t);

                verify(delegate).trace(marker, "format", t);
            }
        }

        @Nested
        class Debug {
            @Test
            void isEnabled() {
                when(delegate.isDebugEnabled()).thenReturn(true);

                assertTrue(underTest.isDebugEnabled());
            }

            @Test
            void format() {
                underTest.debug("format");

                verify(delegate).debug("format");
            }

            @Test
            void formatObject(@Mock final Object arg) {
                underTest.debug("format", arg);

                verify(delegate).debug("format", arg);
            }

            @Test
            void formatObjectObject(@Mock final Object arg1, @Mock final Object arg2) {
                underTest.debug("format", arg1, arg2);

                verify(delegate).debug("format", arg1, arg2);
            }

            @Test
            void formatArguments(@Mock final Object arg1,
                                 @Mock final Object arg2,
                                 @Mock final Object arg3) {
                underTest.debug("format", arg1, arg2, arg3);

                verify(delegate).debug("format", arg1, arg2, arg3);
            }

            @Test
            void formatThrowable(@Mock final Throwable t) {
                underTest.debug("format", t);

                verify(delegate).debug("format", t);
            }
        }

        @Nested
        class DebugMarker {
            @Mock
            private Marker marker;

            @Test
            void isEnabled() {
                when(delegate.isDebugEnabled(marker)).thenReturn(true);

                assertTrue(underTest.isDebugEnabled(marker));
            }

            @Test
            void format() {
                underTest.debug(marker, "format");

                verify(delegate).debug(marker, "format");
            }

            @Test
            void formatObject(@Mock final Object arg) {
                underTest.debug(marker, "format", arg);

                verify(delegate).debug(marker, "format", arg);
            }

            @Test
            void formatObjectObject(@Mock final Object arg1, @Mock final Object arg2) {
                underTest.debug(marker, "format", arg1, arg2);

                verify(delegate).debug(marker, "format", arg1, arg2);
            }

            @Test
            void formatArguments(@Mock final Object arg1,
                                 @Mock final Object arg2,
                                 @Mock final Object arg3) {
                underTest.debug(marker, "format", arg1, arg2, arg3);

                verify(delegate).debug(marker, "format", arg1, arg2, arg3);
            }

            @Test
            void formatThrowable(@Mock final Throwable t) {
                underTest.debug(marker, "format", t);

                verify(delegate).debug(marker, "format", t);
            }
        }

        @Nested
        class Info {
            @Test
            void isEnabled() {
                when(delegate.isInfoEnabled()).thenReturn(true);

                assertTrue(underTest.isInfoEnabled());
            }

            @Test
            void format() {
                underTest.info("format");

                verify(delegate).info("format");
            }

            @Test
            void formatObject(@Mock final Object arg) {
                underTest.info("format", arg);

                verify(delegate).info("format", arg);
            }

            @Test
            void formatObjectObject(@Mock final Object arg1, @Mock final Object arg2) {
                underTest.info("format", arg1, arg2);

                verify(delegate).info("format", arg1, arg2);
            }

            @Test
            void formatArguments(@Mock final Object arg1,
                                 @Mock final Object arg2,
                                 @Mock final Object arg3) {
                underTest.info("format", arg1, arg2, arg3);

                verify(delegate).info("format", arg1, arg2, arg3);
            }

            @Test
            void formatThrowable(@Mock final Throwable t) {
                underTest.info("format", t);

                verify(delegate).info("format", t);
            }
        }

        @Nested
        class InfoMarker {
            @Mock
            private Marker marker;

            @Test
            void isEnabled() {
                when(delegate.isInfoEnabled(marker)).thenReturn(true);

                assertTrue(underTest.isInfoEnabled(marker));
            }

            @Test
            void format() {
                underTest.info(marker, "format");

                verify(delegate).info(marker, "format");
            }

            @Test
            void formatObject(@Mock final Object arg) {
                underTest.info(marker, "format", arg);

                verify(delegate).info(marker, "format", arg);
            }

            @Test
            void formatObjectObject(@Mock final Object arg1, @Mock final Object arg2) {
                underTest.info(marker, "format", arg1, arg2);

                verify(delegate).info(marker, "format", arg1, arg2);
            }

            @Test
            void formatArguments(@Mock final Object arg1,
                                 @Mock final Object arg2,
                                 @Mock final Object arg3) {
                underTest.info(marker, "format", arg1, arg2, arg3);

                verify(delegate).info(marker, "format", arg1, arg2, arg3);
            }

            @Test
            void formatThrowable(@Mock final Throwable t) {
                underTest.info(marker, "format", t);

                verify(delegate).info(marker, "format", t);
            }
        }

        @Nested
        class Warn {
            @Test
            void isEnabled() {
                when(delegate.isWarnEnabled()).thenReturn(true);

                assertTrue(underTest.isWarnEnabled());
            }

            @Test
            void format() {
                underTest.warn("format");

                verify(delegate).warn("format");
            }

            @Test
            void formatObject(@Mock final Object arg) {
                underTest.warn("format", arg);

                verify(delegate).warn("format", arg);
            }

            @Test
            void formatObjectObject(@Mock final Object arg1, @Mock final Object arg2) {
                underTest.warn("format", arg1, arg2);

                verify(delegate).warn("format", arg1, arg2);
            }

            @Test
            void formatArguments(@Mock final Object arg1,
                                 @Mock final Object arg2,
                                 @Mock final Object arg3) {
                underTest.warn("format", arg1, arg2, arg3);

                verify(delegate).warn("format", arg1, arg2, arg3);
            }

            @Test
            void formatThrowable(@Mock final Throwable t) {
                underTest.warn("format", t);

                verify(delegate).warn("format", t);
            }
        }

        @Nested
        class WarnMarker {
            @Mock
            private Marker marker;

            @Test
            void isEnabled() {
                when(delegate.isWarnEnabled(marker)).thenReturn(true);

                assertTrue(underTest.isWarnEnabled(marker));
            }

            @Test
            void format() {
                underTest.warn(marker, "format");

                verify(delegate).warn(marker, "format");
            }

            @Test
            void formatObject(@Mock final Object arg) {
                underTest.warn(marker, "format", arg);

                verify(delegate).warn(marker, "format", arg);
            }

            @Test
            void formatObjectObject(@Mock final Object arg1, @Mock final Object arg2) {
                underTest.warn(marker, "format", arg1, arg2);

                verify(delegate).warn(marker, "format", arg1, arg2);
            }

            @Test
            void formatArguments(@Mock final Object arg1,
                                 @Mock final Object arg2,
                                 @Mock final Object arg3) {
                underTest.warn(marker, "format", arg1, arg2, arg3);

                verify(delegate).warn(marker, "format", arg1, arg2, arg3);
            }

            @Test
            void formatThrowable(@Mock final Throwable t) {
                underTest.warn(marker, "format", t);

                verify(delegate).warn(marker, "format", t);
            }
        }

        @Nested
        class Error {
            @Test
            void isEnabled() {
                when(delegate.isErrorEnabled()).thenReturn(true);

                assertTrue(underTest.isErrorEnabled());
            }

            @Test
            void format() {
                underTest.error("format");

                verify(delegate).error("format");
            }

            @Test
            void formatObject(@Mock final Object arg) {
                underTest.error("format", arg);

                verify(delegate).error("format", arg);
            }

            @Test
            void formatObjectObject(@Mock final Object arg1, @Mock final Object arg2) {
                underTest.error("format", arg1, arg2);

                verify(delegate).error("format", arg1, arg2);
            }

            @Test
            void formatArguments(@Mock final Object arg1,
                                 @Mock final Object arg2,
                                 @Mock final Object arg3) {
                underTest.error("format", arg1, arg2, arg3);

                verify(delegate).error("format", arg1, arg2, arg3);
            }

            @Test
            void formatThrowable(@Mock final Throwable t) {
                underTest.error("format", t);

                verify(delegate).error("format", t);
            }
        }

        @Nested
        class ErrorMarker {
            @Mock
            private Marker marker;

            @Test
            void isEnabled() {
                when(delegate.isErrorEnabled(marker)).thenReturn(true);

                assertTrue(underTest.isErrorEnabled(marker));
            }

            @Test
            void format() {
                underTest.error(marker, "format");

                verify(delegate).error(marker, "format");
            }

            @Test
            void formatObject(@Mock final Object arg) {
                underTest.error(marker, "format", arg);

                verify(delegate).error(marker, "format", arg);
            }

            @Test
            void formatObjectObject(@Mock final Object arg1, @Mock final Object arg2) {
                underTest.error(marker, "format", arg1, arg2);

                verify(delegate).error(marker, "format", arg1, arg2);
            }

            @Test
            void formatArguments(@Mock final Object arg1,
                                 @Mock final Object arg2,
                                 @Mock final Object arg3) {
                underTest.error(marker, "format", arg1, arg2, arg3);

                verify(delegate).error(marker, "format", arg1, arg2, arg3);
            }

            @Test
            void formatThrowable(@Mock final Throwable t) {
                underTest.error(marker, "format", t);

                verify(delegate).error(marker, "format", t);
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
                    when(delegate.isTraceEnabled()).thenReturn(true);
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
                    when(delegate.isTraceEnabled()).thenReturn(false);
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
        class TraceMarker {
            @Mock
            private Marker marker;

            @Nested
            class WhenEnabled {
                @BeforeEach
                void setUp() {
                    when(delegate.isTraceEnabled(marker)).thenReturn(true);
                }

                @Test
                void formatObject(@Mock final Supplier<Object> arg) {
                    underTest.trace(marker, "format", arg);

                    verify(arg).get();
                }

                @Test
                void formatObjectObject(@Mock final Supplier<Object> arg1,
                                        @Mock final Supplier<Object> arg2) {
                    underTest.trace(marker, "format", arg1, arg2);

                    verify(arg1).get();
                    verify(arg2).get();
                }

                @Test
                void formatArguments(@Mock final Supplier<Object> arg1,
                                     @Mock final Supplier<Object> arg2,
                                     @Mock final Supplier<Object> arg3) {
                    underTest.trace(marker, "format", arg1, arg2, arg3);

                    verify(arg1).get();
                    verify(arg2).get();
                    verify(arg3).get();
                }
            }

            @Nested
            class WhenDisabled {
                @BeforeEach
                void setUp() {
                    when(delegate.isTraceEnabled(marker)).thenReturn(false);
                }

                @Test
                void formatObject(@Mock final Supplier<Object> arg) {
                    underTest.trace(marker, "format", arg);

                    verify(arg, never()).get();
                }

                @Test
                void formatObjectObject(@Mock final Supplier<Object> arg1,
                                        @Mock final Supplier<Object> arg2) {
                    underTest.trace(marker, "format", arg1, arg2);

                    verify(arg1, never()).get();
                    verify(arg2, never()).get();
                }

                @Test
                void formatArguments(@Mock final Supplier<Object> arg1,
                                     @Mock final Supplier<Object> arg2,
                                     @Mock final Supplier<Object> arg3) {
                    underTest.trace(marker, "format", arg1, arg2, arg3);

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
                    when(delegate.isDebugEnabled()).thenReturn(true);
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
                    when(delegate.isDebugEnabled()).thenReturn(false);
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
        class DebugMarker {
            @Mock
            private Marker marker;

            @Nested
            class WhenEnabled {
                @BeforeEach
                void setUp() {
                    when(delegate.isDebugEnabled(marker)).thenReturn(true);
                }

                @Test
                void formatObject(@Mock final Supplier<Object> arg) {
                    underTest.debug(marker, "format", arg);

                    verify(arg).get();
                }

                @Test
                void formatObjectObject(@Mock final Supplier<Object> arg1,
                                        @Mock final Supplier<Object> arg2) {
                    underTest.debug(marker, "format", arg1, arg2);

                    verify(arg1).get();
                    verify(arg2).get();
                }

                @Test
                void formatArguments(@Mock final Supplier<Object> arg1,
                                     @Mock final Supplier<Object> arg2,
                                     @Mock final Supplier<Object> arg3) {
                    underTest.debug(marker, "format", arg1, arg2, arg3);

                    verify(arg1).get();
                    verify(arg2).get();
                    verify(arg3).get();
                }
            }

            @Nested
            class WhenDisabled {
                @BeforeEach
                void setUp() {
                    when(delegate.isDebugEnabled(marker)).thenReturn(false);
                }

                @Test
                void formatObject(@Mock final Supplier<Object> arg) {
                    underTest.debug(marker, "format", arg);

                    verify(arg, never()).get();
                }

                @Test
                void formatObjectObject(@Mock final Supplier<Object> arg1,
                                        @Mock final Supplier<Object> arg2) {
                    underTest.debug(marker, "format", arg1, arg2);

                    verify(arg1, never()).get();
                    verify(arg2, never()).get();
                }

                @Test
                void formatArguments(@Mock final Supplier<Object> arg1,
                                     @Mock final Supplier<Object> arg2,
                                     @Mock final Supplier<Object> arg3) {
                    underTest.debug(marker, "format", arg1, arg2, arg3);

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
                    when(delegate.isInfoEnabled()).thenReturn(true);
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
                    when(delegate.isInfoEnabled()).thenReturn(false);
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
        class InfoMarker {
            @Mock
            private Marker marker;

            @Nested
            class WhenEnabled {
                @BeforeEach
                void setUp() {
                    when(delegate.isInfoEnabled(marker)).thenReturn(true);
                }

                @Test
                void formatObject(@Mock final Supplier<Object> arg) {
                    underTest.info(marker, "format", arg);

                    verify(arg).get();
                }

                @Test
                void formatObjectObject(@Mock final Supplier<Object> arg1,
                                        @Mock final Supplier<Object> arg2) {
                    underTest.info(marker, "format", arg1, arg2);

                    verify(arg1).get();
                    verify(arg2).get();
                }

                @Test
                void formatArguments(@Mock final Supplier<Object> arg1,
                                     @Mock final Supplier<Object> arg2,
                                     @Mock final Supplier<Object> arg3) {
                    underTest.info(marker, "format", arg1, arg2, arg3);

                    verify(arg1).get();
                    verify(arg2).get();
                    verify(arg3).get();
                }
            }

            @Nested
            class WhenDisabled {
                @BeforeEach
                void setUp() {
                    when(delegate.isInfoEnabled(marker)).thenReturn(false);
                }

                @Test
                void formatObject(@Mock final Supplier<Object> arg) {
                    underTest.info(marker, "format", arg);

                    verify(arg, never()).get();
                }

                @Test
                void formatObjectObject(@Mock final Supplier<Object> arg1,
                                        @Mock final Supplier<Object> arg2) {
                    underTest.info(marker, "format", arg1, arg2);

                    verify(arg1, never()).get();
                    verify(arg2, never()).get();
                }

                @Test
                void formatArguments(@Mock final Supplier<Object> arg1,
                                     @Mock final Supplier<Object> arg2,
                                     @Mock final Supplier<Object> arg3) {
                    underTest.info(marker, "format", arg1, arg2, arg3);

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
                    when(delegate.isWarnEnabled()).thenReturn(true);
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
                    when(delegate.isWarnEnabled()).thenReturn(false);
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
        class WarnMarker {
            @Mock
            private Marker marker;

            @Nested
            class WhenEnabled {
                @BeforeEach
                void setUp() {
                    when(delegate.isWarnEnabled(marker)).thenReturn(true);
                }

                @Test
                void formatObject(@Mock final Supplier<Object> arg) {
                    underTest.warn(marker, "format", arg);

                    verify(arg).get();
                }

                @Test
                void formatObjectObject(@Mock final Supplier<Object> arg1,
                                        @Mock final Supplier<Object> arg2) {
                    underTest.warn(marker, "format", arg1, arg2);

                    verify(arg1).get();
                    verify(arg2).get();
                }

                @Test
                void formatArguments(@Mock final Supplier<Object> arg1,
                                     @Mock final Supplier<Object> arg2,
                                     @Mock final Supplier<Object> arg3) {
                    underTest.warn(marker, "format", arg1, arg2, arg3);

                    verify(arg1).get();
                    verify(arg2).get();
                    verify(arg3).get();
                }
            }

            @Nested
            class WhenDisabled {
                @BeforeEach
                void setUp() {
                    when(delegate.isWarnEnabled(marker)).thenReturn(false);
                }

                @Test
                void formatObject(@Mock final Supplier<Object> arg) {
                    underTest.warn(marker, "format", arg);

                    verify(arg, never()).get();
                }

                @Test
                void formatObjectObject(@Mock final Supplier<Object> arg1,
                                        @Mock final Supplier<Object> arg2) {
                    underTest.warn(marker, "format", arg1, arg2);

                    verify(arg1, never()).get();
                    verify(arg2, never()).get();
                }

                @Test
                void formatArguments(@Mock final Supplier<Object> arg1,
                                     @Mock final Supplier<Object> arg2,
                                     @Mock final Supplier<Object> arg3) {
                    underTest.warn(marker, "format", arg1, arg2, arg3);

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
                    when(delegate.isErrorEnabled()).thenReturn(true);
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
                    when(delegate.isErrorEnabled()).thenReturn(false);
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

        @Nested
        class ErrorMarker {
            @Mock
            private Marker marker;

            @Nested
            class WhenEnabled {
                @BeforeEach
                void setUp() {
                    when(delegate.isErrorEnabled(marker)).thenReturn(true);
                }

                @Test
                void formatObject(@Mock final Supplier<Object> arg) {
                    underTest.error(marker, "format", arg);

                    verify(arg).get();
                }

                @Test
                void formatObjectObject(@Mock final Supplier<Object> arg1,
                                        @Mock final Supplier<Object> arg2) {
                    underTest.error(marker, "format", arg1, arg2);

                    verify(arg1).get();
                    verify(arg2).get();
                }

                @Test
                void formatArguments(@Mock final Supplier<Object> arg1,
                                     @Mock final Supplier<Object> arg2,
                                     @Mock final Supplier<Object> arg3) {
                    underTest.error(marker, "format", arg1, arg2, arg3);

                    verify(arg1).get();
                    verify(arg2).get();
                    verify(arg3).get();
                }
            }

            @Nested
            class WhenDisabled {
                @BeforeEach
                void setUp() {
                    when(delegate.isErrorEnabled(marker)).thenReturn(false);
                }

                @Test
                void formatObject(@Mock final Supplier<Object> arg) {
                    underTest.error(marker, "format", arg);

                    verify(arg, never()).get();
                }

                @Test
                void formatObjectObject(@Mock final Supplier<Object> arg1,
                                        @Mock final Supplier<Object> arg2) {
                    underTest.error(marker, "format", arg1, arg2);

                    verify(arg1, never()).get();
                    verify(arg2, never()).get();
                }

                @Test
                void formatArguments(@Mock final Supplier<Object> arg1,
                                     @Mock final Supplier<Object> arg2,
                                     @Mock final Supplier<Object> arg3) {
                    underTest.error(marker, "format", arg1, arg2, arg3);

                    verify(arg1, never()).get();
                    verify(arg2, never()).get();
                    verify(arg3, never()).get();
                }
            }
        }
    }
}