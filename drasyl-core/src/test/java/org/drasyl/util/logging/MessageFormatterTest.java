/*
 * Copyright 2017 The Netty Project
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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.drasyl.util.logging.MessageFormatter.arrayFormat;
import static org.drasyl.util.logging.MessageFormatter.format;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class MessageFormatterTest {
    @Nested
    class Format {
    }

    @Nested
    class ArrayFormat {
        @Test
        void whenArrayIsEmpty() {
            assertEquals(new FormattingTuple("No value."), arrayFormat("No value.", new Object[]{}));
        }

        @Test
        void whenPatternIsNullAndThrowableLastArgument(@Mock final Throwable throwable) {
            assertEquals(new FormattingTuple(null, throwable), arrayFormat(null, new Object[]{
                    throwable
            }));
        }

        @Test
        void whenPatternIsSimpleString(@Mock final Object arg) {
            assertEquals(new FormattingTuple("No value.", null), arrayFormat("No value.", new Object[]{
                    arg
            }));
        }

        @Test
        void whenPatternContainEscapedPlaceholder() {
            assertEquals(new FormattingTuple("Escaped {} subst"), arrayFormat("Escaped \\{} subst", new Object[]{}));
        }
    }

    @Test
    void testNull() {
        assertEquals(new FormattingTuple(null), format(null, 1));
    }

    @Test
    void nullParametersShouldBeHandledWithoutBarfing() {
        assertEquals(new FormattingTuple("Value is null."), format("Value is {}.", null));

        assertEquals(new FormattingTuple("Val1 is null, val2 is null."), format("Val1 is {}, val2 is {}.", null, null));

        assertEquals(new FormattingTuple("Val1 is 1, val2 is null."), format("Val1 is {}, val2 is {}.", 1, null));

        assertEquals(new FormattingTuple("Val1 is null, val2 is 2."), format("Val1 is {}, val2 is {}.", null, 2));

        assertEquals(new FormattingTuple("Val1 is null, val2 is null, val3 is null"), arrayFormat("Val1 is {}, val2 is {}, val3 is {}", new Integer[]{
                null,
                null,
                null
        }));

        assertEquals(new FormattingTuple("Val1 is null, val2 is 2, val3 is 3"), arrayFormat(
                "Val1 is {}, val2 is {}, val3 is {}", new Integer[]{ null, 2, 3 }));

        assertEquals(new FormattingTuple("Val1 is null, val2 is null, val3 is 3"), arrayFormat(
                "Val1 is {}, val2 is {}, val3 is {}", new Integer[]{ null, null, 3 }));
    }

    @Test
    void verifyOneParameterIsHandledCorrectly() {
        assertEquals(new FormattingTuple("Value is 3."), format("Value is {}.", 3));

        assertEquals(new FormattingTuple("Value is {"), format("Value is {", 3));

        assertEquals(new FormattingTuple("3 is larger than 2."), format("{} is larger than 2.", 3));

        assertEquals(new FormattingTuple("No subst"), format("No subst", 3));

        assertEquals(new FormattingTuple("Incorrect {subst"), format("Incorrect {subst", 3));

        assertEquals(new FormattingTuple("Value is {bla} 3"), format("Value is {bla} {}", 3));

        assertEquals(new FormattingTuple("Escaped {} subst"), format("Escaped \\{} subst", 3));

        assertEquals(new FormattingTuple("{Escaped"), format("{Escaped", 3));

        assertEquals(new FormattingTuple("{}Escaped"), format("\\{}Escaped", 3));

        assertEquals(new FormattingTuple("File name is {App folder.zip}."), format("File name is {{}}.", "App folder.zip"));

        // escaping the escape character
        assertEquals(new FormattingTuple("File name is C:\\App folder.zip."), format("File name is C:\\\\{}.", "App folder.zip"));
    }

    @Test
    void testTwoParameters() {
        assertEquals(new FormattingTuple("Value 1 is smaller than 2."), format("Value {} is smaller than {}.", 1, 2));

        assertEquals(new FormattingTuple("Value 1 is smaller than 2"), format("Value {} is smaller than {}", 1, 2));

        assertEquals(new FormattingTuple("12"), format("{}{}", 1, 2));

        assertEquals(new FormattingTuple("Val1=1, Val2={"), format("Val1={}, Val2={", 1, 2));

        assertEquals(new FormattingTuple("Value 1 is smaller than {}"), format("Value {} is smaller than \\{}", 1, 2));

        assertEquals(new FormattingTuple("Value 1 is smaller than {} tail"), format("Value {} is smaller than \\{} tail", 1, 2));

        assertEquals(new FormattingTuple("Value 1 is smaller than \\{"), format("Value {} is smaller than \\{", 1, 2));

        assertEquals(new FormattingTuple("Value 1 is smaller than {tail"), format("Value {} is smaller than {tail", 1, 2));

        assertEquals(new FormattingTuple("Value {} is smaller than 1"), format("Value \\{} is smaller than {}", 1, 2));
    }

    @Test
    void testExceptionIn_toString() {
        final Object o = new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("a");
            }
        };
        assertEquals(new FormattingTuple("Troublesome object [FAILED toString()]"), format("Troublesome object {}", o));
    }

    @Test
    void testNullArray() {
        final String msg0 = "msg0";
        final String msg1 = "msg1 {}";
        final String msg2 = "msg2 {} {}";
        final String msg3 = "msg3 {} {} {}";

        final Object[] args = null;

        assertEquals(new FormattingTuple(msg0), arrayFormat(msg0, args));

        assertEquals(new FormattingTuple(msg1), arrayFormat(msg1, args));

        assertEquals(new FormattingTuple(msg2), arrayFormat(msg2, args));

        assertEquals(new FormattingTuple(msg3), arrayFormat(msg3, args));
    }

    // tests the case when the parameters are supplied in a single array
    @Test
    void testArrayFormat() {
        final Integer[] ia0 = { 1, 2, 3 };

        assertEquals(new FormattingTuple("Value 1 is smaller than 2 and 3."), arrayFormat("Value {} is smaller than {} and {}.", ia0));

        assertEquals(new FormattingTuple("123"), arrayFormat("{}{}{}", ia0));

        assertEquals(new FormattingTuple("Value 1 is smaller than 2."), arrayFormat("Value {} is smaller than {}.", ia0));

        assertEquals(new FormattingTuple("Value 1 is smaller than 2"), arrayFormat("Value {} is smaller than {}", ia0));

        assertEquals(new FormattingTuple("Val=1, {, Val=2"), arrayFormat("Val={}, {, Val={}", ia0));

        assertEquals(new FormattingTuple("Val=1, {, Val=2"), arrayFormat("Val={}, {, Val={}", ia0));

        assertEquals(new FormattingTuple("Val1=1, Val2={"), arrayFormat("Val1={}, Val2={", ia0));
    }

    @Test
    void testArrayValues() {
        final Integer[] p1 = { 2, 3 };

        assertEquals(new FormattingTuple("1[2, 3]"), format("{}{}", 1, p1));

        // Integer[]
        assertEquals(new FormattingTuple("a[2, 3]"), arrayFormat("{}{}", new Object[]{ "a", p1 }));

        // byte[]
        assertEquals(new FormattingTuple("a[1, 2]"), arrayFormat("{}{}", new Object[]{
                "a",
                new byte[]{ 1, 2 }
        }));

        // int[]
        assertEquals(new FormattingTuple("a[1, 2]"), arrayFormat("{}{}", new Object[]{
                "a",
                new int[]{ 1, 2 }
        }));

        // float[]
        assertEquals(new FormattingTuple("a[1.0, 2.0]"), arrayFormat("{}{}", new Object[]{
                "a",
                new float[]{ 1, 2 }
        }));

        // double[]
        assertEquals(new FormattingTuple("a[1.0, 2.0]"), arrayFormat("{}{}", new Object[]{
                "a",
                new double[]{ 1, 2 }
        }));
    }

    @Test
    void testMultiDimensionalArrayValues() {
        final Integer[] ia0 = { 1, 2, 3 };
        final Integer[] ia1 = { 10, 20, 30 };

        final Integer[][] multiIntegerA = { ia0, ia1 };
        assertEquals(new FormattingTuple("a[[1, 2, 3], [10, 20, 30]]"), arrayFormat("{}{}", new Object[]{
                "a",
                multiIntegerA
        }));

        final int[][] multiIntA = { { 1, 2 }, { 10, 20 } };
        assertEquals(new FormattingTuple("a[[1, 2], [10, 20]]"), arrayFormat("{}{}", new Object[]{
                "a",
                multiIntA
        }));

        final float[][] multiFloatA = { { 1, 2 }, { 10, 20 } };
        assertEquals(new FormattingTuple("a[[1.0, 2.0], [10.0, 20.0]]"), arrayFormat("{}{}", new Object[]{
                "a",
                multiFloatA
        }));

        final Object[][] multiOA = { ia0, ia1 };
        assertEquals(new FormattingTuple("a[[1, 2, 3], [10, 20, 30]]"), arrayFormat("{}{}", new Object[]{
                "a",
                multiOA
        }));

        final Object[][][] _3DOA = { multiOA, multiOA };
        assertEquals(new FormattingTuple("a[[[1, 2, 3], [10, 20, 30]], [[1, 2, 3], [10, 20, 30]]]"), arrayFormat("{}{}", new Object[]{
                "a",
                _3DOA
        }));

        final Byte[] ba0 = { 0, Byte.MAX_VALUE, Byte.MIN_VALUE };
        final Short[] sa0 = { 0, Short.MIN_VALUE, Short.MAX_VALUE };
        assertEquals(new FormattingTuple("[[0, 127, -128], [0, -32768, 32767]]{}[10, 20, 30]"), arrayFormat("{}\\{}{}", new Object[]{
                new Object[]{ ba0, sa0 },
                ia1
        }));
    }

    @Test
    void testCyclicArrays() {
        final Object[] cyclicA = new Object[1];
        cyclicA[0] = cyclicA;
        assertEquals(new FormattingTuple("[[...]]"), arrayFormat("{}", cyclicA));

        final Object[] a = new Object[2];
        a[0] = 1;
        final Object[] c = { 3, a };
        final Object[] b = { 2, c };
        a[1] = b;
        assertEquals(new FormattingTuple("1[2, [3, [1, [...]]]]"),
                arrayFormat("{}{}", a));
    }

    @Test
    void testArrayThrowable() {
        FormattingTuple ft;
        final Throwable t = new Throwable();
        final Object[] ia = { 1, 2, 3, t };

        assertEquals(new FormattingTuple("Value 1 is smaller than 2 and 3.", t), arrayFormat("Value {} is smaller than {} and {}.", ia));

        assertEquals(new FormattingTuple("123", t), arrayFormat("{}{}{}", ia));

        assertEquals(new FormattingTuple("Value 1 is smaller than 2.", t), arrayFormat("Value {} is smaller than {}.", ia));

        assertEquals(new FormattingTuple("Value 1 is smaller than 2", t), arrayFormat("Value {} is smaller than {}", ia));

        assertEquals(new FormattingTuple("Val=1, {, Val=2", t), arrayFormat("Val={}, {, Val={}", ia));

        assertEquals(new FormattingTuple("Val=1, \\{, Val=2", t), arrayFormat("Val={}, \\{, Val={}", ia));

        assertEquals(new FormattingTuple("Val1=1, Val2={", t), arrayFormat("Val1={}, Val2={", ia));

        assertEquals(new FormattingTuple("Value 1 is smaller than 2 and 3.", t), arrayFormat("Value {} is smaller than {} and {}.", ia));

        assertEquals(new FormattingTuple("123java.lang.Throwable", null), arrayFormat("{}{}{}{}", ia));
    }
}
