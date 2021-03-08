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
package org.drasyl.cli.command;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.cli.command.wormhole.ReceivingWormholeNode;
import org.drasyl.cli.command.wormhole.SendingWormholeNode;
import org.drasyl.util.ThrowingBiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WormholeCommandTest {
    private ByteArrayOutputStream outStream;
    @SuppressWarnings("FieldCanBeLocal")
    private PrintStream out;
    private ByteArrayOutputStream errStream;
    @SuppressWarnings("FieldCanBeLocal")
    private PrintStream err;
    @Mock
    private Supplier<Scanner> scannerSupplier;
    @Mock
    private ThrowingBiFunction<DrasylConfig, PrintStream, SendingWormholeNode, DrasylException> sendingNodeSupplier;
    @Mock
    private ThrowingBiFunction<DrasylConfig, PrintStream, ReceivingWormholeNode, DrasylException> receivingNodeSupplier;
    @Mock
    private Consumer<Integer> exitSupplier;
    private WormholeCommand underTest;

    @BeforeEach
    void setUp() {
        outStream = new ByteArrayOutputStream();
        out = new PrintStream(outStream, true);
        errStream = new ByteArrayOutputStream();
        err = new PrintStream(errStream, true);
        underTest = new WormholeCommand(out, err, scannerSupplier, sendingNodeSupplier, receivingNodeSupplier, exitSupplier);
    }

    @Nested
    class Help {
        @Mock
        private CommandLine cmd;

        @Test
        void shouldPrintHelp() {
            underTest.help(cmd);

            final String output = outStream.toString();
            assertThat(output, containsString("Transfer a text message from one node to another, safely."));
            assertThat(output, containsString("Usage:" + System.lineSeparator()));
            assertThat(output, containsString("drasyl wormhole [flags]" + System.lineSeparator()));
            assertThat(output, containsString("drasyl wormhole [command]" + System.lineSeparator()));
            assertThat(output, containsString("Flags:" + System.lineSeparator()));
        }
    }

    @Nested
    class Execute {
        @Mock(answer = RETURNS_DEEP_STUBS)
        private CommandLine cmd;

        @Test
        void shouldRequestTextAndStartSendingNode(@Mock(answer = RETURNS_DEEP_STUBS) final SendingWormholeNode node) throws DrasylException {
            when(cmd.getArgList().size()).thenReturn(2);
            when(cmd.getArgList().get(1)).thenReturn("send");
            when(scannerSupplier.get()).thenReturn(new Scanner("Hallo Welt"));
            when(sendingNodeSupplier.apply(any(), any())).thenReturn(node);

            underTest.execute(cmd);

            verify(node).start();
        }

        @Test
        void shouldUseGivenTextAndStartSendingNode(@Mock(answer = RETURNS_DEEP_STUBS) final SendingWormholeNode node) throws DrasylException, ParseException {
            when(cmd.getArgList().size()).thenReturn(2);
            when(cmd.getArgList().get(1)).thenReturn("send");
            when(cmd.hasOption("config")).thenReturn(false);
            when(cmd.hasOption("text")).thenReturn(true);
            when(cmd.getParsedOptionValue("text")).thenReturn("Hallo Welt");
            when(sendingNodeSupplier.apply(any(), any())).thenReturn(node);

            underTest.execute(cmd);

            verify(node).start();
        }

        @Test
        void shouldRequestCodeAndStartReceivingNode(@Mock(answer = RETURNS_DEEP_STUBS) final ReceivingWormholeNode node) throws DrasylException {
            when(cmd.getArgList().size()).thenReturn(2);
            when(cmd.getArgList().get(1)).thenReturn("receive");
            when(scannerSupplier.get()).thenReturn(new Scanner("022e170caf9292de6af36562d2773e62d573e33d09550e1620b9cae75b1a3a98281ff73f2346d55195d0cd274c101c4775"));
            when(receivingNodeSupplier.apply(any(), any())).thenReturn(node);

            underTest.execute(cmd);

            verify(node).start();
        }

        @Test
        void shouldUseGivenCodeAndStartReceivingNode(@Mock(answer = RETURNS_DEEP_STUBS) final ReceivingWormholeNode node) throws DrasylException {
            when(cmd.getArgList().size()).thenReturn(3);
            when(cmd.getArgList().get(1)).thenReturn("receive");
            when(cmd.getArgList().get(2)).thenReturn("022e170caf9292de6af36562d2773e62d573e33d09550e1620b9cae75b1a3a98281ff73f2346d55195d0cd274c101c4775");
            when(receivingNodeSupplier.apply(any(), any())).thenReturn(node);

            underTest.execute(cmd);

            verify(node).start();
        }

        @Test
        void shouldAbortOnInvalidCode(@Mock(answer = RETURNS_DEEP_STUBS) final ReceivingWormholeNode node) throws DrasylException {
            when(cmd.getArgList().size()).thenReturn(3);
            when(cmd.getArgList().get(1)).thenReturn("receive");
            when(cmd.getArgList().get(2)).thenReturn("022e170caf9292de6af36562d2773e62d573e33d09550e1620b9cae75b1a3a9");
            when(receivingNodeSupplier.apply(any(), any())).thenReturn(node);

            underTest.execute(cmd);

            verify(node, never()).requestText(any(), any());

            final String output = errStream.toString();
            assertThat(output, containsString("ERR: Invalid wormhole code supplied: must be at least 66 characters long."));
        }

        @Test
        void shouldThrowExceptionOnUnknownCommand() {
            when(cmd.getArgList().size()).thenReturn(2);
            when(cmd.getArgList().get(1)).thenReturn("foo");

            underTest.execute(cmd);

            final String output = errStream.toString();
            assertThat(output, containsString("ERR: Unknown command \"foo\" for \"drasyl wormhole\"."));
        }

        @Test
        void shouldPrintHelpOnMissingCommand() {
            when(cmd.getArgList().size()).thenReturn(1);

            underTest.execute(cmd);

            final String output = outStream.toString();
            assertThat(output, containsString("Transfer a text message from one node to another, safely."));
        }
    }
}
