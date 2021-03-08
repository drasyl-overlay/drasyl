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
package test.util;

import io.reactivex.rxjava3.observers.BaseTestConsumer;
import org.drasyl.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

@SuppressWarnings("unused")
public final class RxJavaTestUtil {
    private RxJavaTestUtil() {
        // util class
    }

    /**
     * Assert that the {@code TestObserver}/{@code TestSubscriber} received only the specified
     * values in any order.
     *
     * @param values the values expected
     * @return {@code baseTestConsumer}
     */
    @SafeVarargs
    @NonNull
    public static <T, U extends BaseTestConsumer<T, U>> U assertValues(@NonNull final U baseTestConsumer,
                                                                       @NonNull final T... values) {
        final List<T> actualValues = baseTestConsumer.values();
        final int s = actualValues.size();

        if (s != values.length) {
            throw new AssertionError("Value count differs; expected: " + values.length
                    + " " + Arrays.toString(values) + " but was: " + s + " " + actualValues);
        }

        for (final T value : values) {
            if (!actualValues.contains(value)) {
                throw new AssertionError("expected: " + U.valueAndClass(value)
                        + " but was not included");
            }
        }

        return baseTestConsumer;
    }
}
