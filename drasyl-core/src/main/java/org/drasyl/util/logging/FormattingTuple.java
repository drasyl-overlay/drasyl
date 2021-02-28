package org.drasyl.util.logging;

import java.util.Objects;

/**
 * Holds the results of formatting done by {@link MessageFormatter}.
 */
final class FormattingTuple {
    private final String message;
    private final Throwable throwable;

    FormattingTuple(final String message, final Throwable throwable) {
        this.message = message;
        this.throwable = throwable;
    }

    public FormattingTuple(final String message) {
        this(message, null);
    }

    public String getMessage() {
        return message;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FormattingTuple that = (FormattingTuple) o;
        return Objects.equals(message, that.message) && Objects.equals(throwable, that.throwable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, throwable);
    }

    @Override
    public String toString() {
        return "FormattingTuple{" +
                "message='" + message + '\'' +
                ", throwable=" + throwable +
                '}';
    }
}
