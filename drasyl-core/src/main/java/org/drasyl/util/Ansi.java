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
package org.drasyl.util;

import java.util.ArrayList;
import java.util.List;

import static org.drasyl.util.Ansi.Color.BLACK;
import static org.drasyl.util.Ansi.Color.BLUE;
import static org.drasyl.util.Ansi.Color.CYAN;
import static org.drasyl.util.Ansi.Color.GREEN;
import static org.drasyl.util.Ansi.Color.MAGENTA;
import static org.drasyl.util.Ansi.Color.RED;
import static org.drasyl.util.Ansi.Color.WHITE;
import static org.drasyl.util.Ansi.Color.YELLOW;

/**
 * Utility to colorize and style Strings with ANSI escape sequences.
 * <p>
 * Usage:
 * <pre><code>
 * String msg1 = ansi().red().onBlue().swap().format("Hello %s", name);
 * String msg2 = ansi().color(MAGENTA).background(GREEN).format("Hello World");
 * </code></pre>
 */
public final class Ansi {
    private static final int COLOR_BASE = 30;
    private static final int BACKGROUND_BASE = 40;
    public static final String RESET = "\u001B[0m";
    // styles
    private static final String BOLD = "\u001b[1m";
    private static final String ITALIC = "\u001b[3m";
    private static final String UNDERLINE = "\u001b[4m";
    private static final String SWAP = "\u001b[7m";
    private final List<String> codes;

    private Ansi(final List<String> codes) {
        this.codes = List.copyOf(codes);
    }

    private Ansi code(final String style) {
        final List<String> newCodes = new ArrayList<>(codes);
        newCodes.add(style);
        return new Ansi(newCodes);
    }

    @SuppressWarnings("java:S1845")
    public Ansi reset() {
        return code(RESET);
    }

    // colors
    public Ansi color(final Color color) {
        return code("\u001B[" + (COLOR_BASE + color.code) + "m");
    }

    public Ansi black() {
        return color(BLACK);
    }

    public Ansi red() {
        return color(RED);
    }

    public Ansi green() {
        return color(GREEN);
    }

    public Ansi yellow() {
        return color(YELLOW);
    }

    public Ansi blue() {
        return color(BLUE);
    }

    public Ansi purple() {
        return color(MAGENTA);
    }

    public Ansi cyan() {
        return color(CYAN);
    }

    public Ansi white() {
        return color(WHITE);
    }

    // background colors
    public Ansi background(final Color color) {
        return code("\u001B[" + (BACKGROUND_BASE + color.code) + "m");
    }

    public Ansi onBlack() {
        return background(BLACK);
    }

    public Ansi onRed() {
        return background(RED);
    }

    public Ansi onGreen() {
        return background(GREEN);
    }

    public Ansi onYellow() {
        return background(YELLOW);
    }

    public Ansi onBlue() {
        return background(BLUE);
    }

    public Ansi onMagenta() {
        return background(MAGENTA);
    }

    public Ansi onCyan() {
        return background(CYAN);
    }

    public Ansi onWhite() {
        return background(WHITE);
    }

    // styles
    public Ansi bold() {
        return code(BOLD);
    }

    public Ansi italic() {
        return code(ITALIC);
    }

    public Ansi underline() {
        return code(UNDERLINE);
    }

    public Ansi swap() {
        return code(SWAP);
    }

    public String format(final String format) {
        return String.join("", codes) + format + RESET;
    }

    /**
     * @throws java.util.IllegalFormatException If a format string contains an illegal syntax, a
     *                                          format specifier that is incompatible with the given
     *                                          arguments, insufficient arguments given the format
     *                                          string, or other illegal conditions.
     */
    public String format(final String format, final Object... args) {
        return String.join("", codes) + String.format(format, args) + RESET;
    }

    public static Ansi ansi() {
        return new Ansi(List.of());
    }

    public enum Color {
        BLACK((short) 0),
        RED((short) 1),
        GREEN((short) 2),
        YELLOW((short) 3),
        BLUE((short) 4),
        MAGENTA((short) 5),
        CYAN((short) 6),
        WHITE((short) 7);
        private final short code;

        Color(final short code) {
            this.code = code;
        }
    }
}
