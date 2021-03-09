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

import org.junit.jupiter.api.Test;

import static org.drasyl.util.Ansi.ansi;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AnsiTest {
    @Test
    void shouldApplyGivenAnsiCodesToString() {
        assertEquals("\u001B[30m\u001B[47m\u001B[7mHello, World!\u001B[0m", ansi().black().onWhite().swap().format("Hello, %s!", "World"));
        assertEquals("\u001B[31m\u001B[44mHello World\u001B[0m", ansi().red().onBlue().format("Hello World"));
        assertEquals("\u001B[32m\u001B[40m\u001B[1mHello World\u001B[0m", ansi().green().onBlack().bold().format("Hello World"));
        assertEquals("\u001B[33m\u001B[3m\u001B[4m\u001B[41mHello World\u001B[0m", ansi().yellow().italic().underline().onRed().format("Hello World"));
        assertEquals("\u001B[34m\u001B[35m\u001B[42mHello World\u001B[0m", ansi().blue().purple().onGreen().format("Hello World"));
        assertEquals("\u001B[37m\u001B[43mHello World\u001B[0m", ansi().white().onYellow().format("Hello World"));
        assertEquals("\u001B[36mHello World\u001B[0m", ansi().cyan().format("Hello World"));
        assertEquals("\u001B[44mHello World\u001B[0m", ansi().onBlue().format("Hello World"));
        assertEquals("\u001B[45mHello World\u001B[0m", ansi().onMagenta().format("Hello World"));
        assertEquals("\u001B[46m\u001B[47mHello World\u001B[0m", ansi().onCyan().onWhite().format("Hello World"));
        assertEquals("\u001B[46m\u001B[47m\u001B[0mHello World\u001B[0m", ansi().onCyan().onWhite().reset().format("Hello World"));
    }
}
