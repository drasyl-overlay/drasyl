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
package org.drasyl.util;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * This class wraps an object of type {@code T} and serves as a write once read many (worm) memory.
 *
 * @param <T> the type of the worm object
 */
@SuppressWarnings({ "java:S2789", "OptionalUsedAsFieldOrParameterType", "OptionalAssignedToNull" })
public class Worm<T> {
    private Optional<T> value;

    Worm() {
    }

    Worm(final T value) {
        this.value = Optional.ofNullable(value);
    }

    /**
     * If a value is present, returns the value, otherwise throws {@code NoSuchElementException}.
     *
     * @return the value described by this {@code Worm}
     * @throws NoSuchElementException if no value is present
     */
    public T get() {
        synchronized (this) {
            if (value == null) {
                throw new NoSuchElementException("No value present");
            }
            else {
                return value.orElse(null);
            }
        }
    }

    /**
     * Sets the value to {@code value},
     *
     * @param value the new value
     * @throws IllegalStateException if value is already present
     */
    public void set(final T value) {
        synchronized (this) {
            if (this.value == null) {
                this.value = Optional.ofNullable(value);
            }
            else {
                throw new IllegalStateException("Value already present");
            }
        }
    }

    /**
     * If no value has been set yet, it will be set to the return of {@code supplier}. Otherwise the
     * existing value is returned.
     *
     * @param supplier is used to compute the value
     * @return the value of this {@code Worm}
     */
    public synchronized T getOrCompute(final Supplier<T> supplier) {
        synchronized (this) {
            if (value == null) {
                value = Optional.ofNullable(supplier.get());
            }

            return value.orElse(null);
        }
    }

    /**
     * If no value has been set yet, it will be set to {@code value}. Otherwise the existing value
     * is returned.
     *
     * @param value is used to set the value
     * @return the value of this {@code Worm}
     */
    public synchronized T getOrSet(final T value) {
        synchronized (this) {
            if (this.value == null) {
                this.value = Optional.ofNullable(value);
            }

            return this.value.orElse(null);
        }
    }

    /**
     * If a value is present, returns {@code true}, otherwise {@code false}.
     *
     * @return {@code true} if a value is present, otherwise {@code false}
     */
    public boolean isPresent() {
        return value != null;
    }

    /**
     * If a value is  not present, returns {@code true}, otherwise {@code false}.
     *
     * @return {@code true} if a value is not present, otherwise {@code false}
     */
    public boolean isEmpty() {
        return value == null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Worm<?> worm = (Worm<?>) o;
        return Objects.deepEquals(value, worm.value);
    }

    @Override
    public String toString() {
        return "Worm{" +
                "value=" + value +
                '}';
    }

    /**
     * Creates a new {@code Worm} object with not set value.
     *
     * @param <T> the type of the {@code Worm} object
     * @return the {@code Worm} object
     */
    public static <T> Worm<T> of() {
        return new Worm<>();
    }

    /**
     * Creates a new {@code Worm} object with value set to {@code value}.
     *
     * @param <T> the type of the {@code Worm} object
     * @return the {@code Worm} object
     */
    public static <T> Worm<T> of(final T value) {
        return new Worm<>(value);
    }
}
