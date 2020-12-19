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
package testutils;

import org.drasyl.util.AnsiColor;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.util.AnsiColor.COLOR_RESET;

public final class TestHelper {
    private static final Logger LOG = LoggerFactory.getLogger(TestHelper.class);
    private static final String DIVIDER = "##################################################";

    /**
     * Print msg to console in color.
     *
     * @param msg   message to print
     * @param color color of the message
     */
    public static void colorizedPrintln(final String msg, final AnsiColor color) {
        LOG.debug(color.getColor() + DIVIDER + msg + DIVIDER + COLOR_RESET.getColor());
    }

    /**
     * Print msg to console in color.
     *
     * @param msg   message to print
     * @param color color of the message
     * @param style style of the message
     */
    public static void colorizedPrintln(final String msg,
                                        final AnsiColor color,
                                        final AnsiColor style) {
        LOG.debug(color.getColor() + style.getColor() + DIVIDER + " " + msg + " " + DIVIDER + COLOR_RESET.getColor());
    }
}