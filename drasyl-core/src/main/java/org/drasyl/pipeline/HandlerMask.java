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

import io.netty.util.concurrent.FastThreadLocal;
import org.drasyl.event.Event;
import org.drasyl.pipeline.address.Address;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Class to compute the mask of a given {@link Handler}. Inspired by the corresponding netty
 * implementation: https://github.com/netty/netty/blob/master/transport/src/main/java/io/netty/channel/ChannelHandlerMask.java
 */
public final class HandlerMask {
    // Using fast bitwise operations to compute if a method must be called
    public static final int EVENT_TRIGGERED_MASK = 1;
    public static final int EXCEPTION_CAUGHT_MASK = 1 << 1;
    public static final int READ_MASK = 1 << 2;
    public static final int WRITE_MASK = 1 << 3;
    public static final int ALL = EVENT_TRIGGERED_MASK |
            EXCEPTION_CAUGHT_MASK | READ_MASK | WRITE_MASK;
    private static final Logger LOG = LoggerFactory.getLogger(HandlerMask.class);
    private static final FastThreadLocal<Map<Class<? extends Handler>, Integer>> MASK_CACHE =
            new FastThreadLocal<>() {
                @Override
                protected Map<Class<? extends Handler>, Integer> initialValue() {
                    return new WeakHashMap<>(32);
                }
            };

    private HandlerMask() {
    }

    /**
     * Returns the mask for a given {@code handlerClass}.
     *
     * @param handlerClass the handler for which the mask should be returned
     * @return the handler mask
     */
    public static int mask(final Class<? extends Handler> handlerClass) {
        final Map<Class<? extends Handler>, Integer> cache = MASK_CACHE.get();
        Integer mask = cache.get(handlerClass);
        if (mask == null) {
            mask = calcMask(handlerClass);
            cache.put(handlerClass, mask);
        }

        return mask;
    }

    /**
     * Calculates the mask for the given {@code handlerClass}.
     *
     * @param handlerClass the handler for which the mask should be calculated
     * @return the handler mask
     */
    private static int calcMask(final Class<? extends Handler> handlerClass) {
        int mask = ALL;

        if (isSkippable(handlerClass, "eventTriggered",
                HandlerContext.class, Event.class, CompletableFuture.class)) {
            mask &= ~EVENT_TRIGGERED_MASK;
        }

        if (isSkippable(handlerClass, "exceptionCaught",
                HandlerContext.class, Exception.class)) {
            mask &= ~EXCEPTION_CAUGHT_MASK;
        }

        if (isSkippable(handlerClass, "read",
                HandlerContext.class, Address.class, Object.class, CompletableFuture.class)) {
            mask &= ~READ_MASK;
        }

        if (isSkippable(handlerClass, "write",
                HandlerContext.class, Address.class, Object.class, CompletableFuture.class)) {
            mask &= ~WRITE_MASK;
        }

        return mask;
    }

    /**
     * This method checks if the given method {@code methodName} with the parameters {@code
     * paramTypes} has the {@link Skip} annotation.
     * <p>
     * If the current {@link SecurityManager} does not allow reflection, {@code false} is returned
     *
     * @param handlerClass the class of the handler
     * @param methodName   the method
     * @param paramTypes   the parameter types of {@code methodName}
     * @return if the given method {@code methodName} has the {@link Skip} annotation
     */
    @SuppressWarnings("java:S1905")
    static boolean isSkippable(final Class<? extends Handler> handlerClass,
                               final String methodName,
                               final Class<?>... paramTypes) {
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<Boolean>) () -> {
                final Method m;
                try {
                    m = handlerClass.getMethod(methodName, paramTypes);
                }
                catch (final NoSuchMethodException e) {
                    LOG.trace("Class {} missing method {}, assume we can not skip execution", () -> handlerClass, () -> methodName);
                    return false;
                }

                return m != null && m.isAnnotationPresent(Skip.class); // avoid GraalVM error: https://github.com/netty/netty/commit/f48d9ad15f9379d808d6553ce18f99894a32cca5
            });
        }
        catch (final Exception e) { // NOSONAR
            return false;
        }
    }
}
