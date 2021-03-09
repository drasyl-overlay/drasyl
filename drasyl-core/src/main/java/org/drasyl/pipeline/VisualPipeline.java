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
package org.drasyl.pipeline;

import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.drasyl.pipeline.skeleton.SimpleInboundHandler;
import org.drasyl.pipeline.skeleton.SimpleOutboundHandler;
import org.drasyl.util.Ansi.Color;

import static org.drasyl.util.Ansi.ansi;

/**
 * Helper class to visualize the {@link Handler} order of a given {@link AbstractPipeline}.
 */
@SuppressWarnings({ "java:S106", "java:S1192", "unused" })
public final class VisualPipeline {
    private static final int MAX_LINE_WIDTH = 76;
    private static final String DIV_LINE = "--------------------------------------------------------------------------------";

    private VisualPipeline() {
        // util class
    }

    /**
     * Prints the current inbound {@link Handler}s in the sequence of execution to the console.
     *
     * @param pipeline the pipeline that should be printed
     * @param mask     the handler mask that should be fulfilled
     */
    public static void printInboundOrder(final AbstractPipeline pipeline, final int mask) {
        AbstractHandlerContext currentContext = pipeline.head.getNext();

        System.out.println("\nInbound Handler Order:");

        printPretty("NETWORK", Color.RED);

        for (; currentContext.getNext() != null;
             currentContext = currentContext.findNextInbound(mask)) {
            printArrow(Color.GREEN);
            printPretty(currentContext.name(), Color.GREEN);
        }

        printArrow(Color.GREEN);
        printPretty("APPLICATION", Color.MAGENTA);
    }

    /**
     * Prints the current outbound {@link SimpleInboundHandler}s in the sequence of execution to the
     * console.
     *
     * @param pipeline the pipeline that should be printed
     * @param mask     the handler mask that should be fulfilled
     */
    public static void printOnlySimpleInboundHandler(final AbstractPipeline pipeline,
                                                     final int mask) {
        AbstractHandlerContext currentContext = pipeline.head;

        System.out.println("\nSimpleInbound Handler Order:");

        printPretty("NETWORK", Color.RED);

        for (;
             currentContext.getNext() != null;
             currentContext = currentContext.findNextInbound(mask)) {
            if (currentContext.handler() instanceof SimpleInboundHandler) {
                printArrow(Color.GREEN);
                printPretty(currentContext.name(), Color.GREEN);
            }
        }

        printArrow(Color.GREEN);
        printPretty("APPLICATION", Color.MAGENTA);
    }

    /**
     * Prints the current outbound {@link Handler}s in the sequence of execution to the console.
     *
     * @param pipeline the pipeline that should be printed
     * @param mask     the handler mask that should be fulfilled
     */
    public static void printOutboundOrder(final AbstractPipeline pipeline, final int mask) {
        AbstractHandlerContext currentContext = pipeline.tail.getPrev();

        System.out.println("\nOutbound Handler Order:");

        printPretty("APPLICATION", Color.MAGENTA);

        for (;
             currentContext.getPrev() != null;
             currentContext = currentContext.findPrevOutbound(mask)) {
            printArrow(Color.BLUE);
            printPretty(currentContext.name(), Color.BLUE);
        }

        printArrow(Color.BLUE);
        printPretty("NETWORK", Color.RED);
    }

    /**
     * Prints the current outbound {@link SimpleOutboundHandler}s in the sequence of execution to
     * the console.
     *
     * @param pipeline the pipeline that should be printed
     * @param mask     the handler mask that should be fulfilled
     */
    public static void printOnlySimpleOutboundHandler(final AbstractPipeline pipeline,
                                                      final int mask) {
        AbstractHandlerContext currentContext = pipeline.tail;

        System.out.println("\nSimpleOutbound Handler Order:");

        printPretty("APPLICATION", Color.MAGENTA);

        for (;
             currentContext.getPrev() != null;
             currentContext = currentContext.findPrevOutbound(mask)) {
            if (currentContext.handler() instanceof SimpleOutboundHandler || currentContext.handler() instanceof SimpleDuplexHandler) {
                printArrow(Color.BLUE);
                printPretty(currentContext.name(), Color.BLUE);
            }
        }

        printArrow(Color.BLUE);
        printPretty("NETWORK", Color.RED);
    }

    private static void printPretty(final String s, final Color color) {
        System.out.println(ansi().color(color).format(DIV_LINE));
        System.out.println(ansi().color(color).format("| %-" + MAX_LINE_WIDTH + "." + MAX_LINE_WIDTH + "s |", s));
        System.out.println(ansi().color(color).format(DIV_LINE));
    }

    private static void printArrow(final Color color) {
        System.out.println(ansi().color(color).format("%41.41s%n", "↓↓↓"));
    }
}
