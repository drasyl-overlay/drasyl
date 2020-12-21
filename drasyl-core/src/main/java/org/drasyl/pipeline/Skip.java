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
package org.drasyl.pipeline;

import org.drasyl.event.Event;
import org.drasyl.pipeline.address.Address;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.CompletableFuture;

/**
 * Indicates that the annotated handler method ({@link Handler#read(HandlerContext, Address, Object,
 * CompletableFuture)}, {@link Handler#write(HandlerContext, Address, Object, CompletableFuture)},
 * {@link Handler#eventTriggered(HandlerContext, Event, CompletableFuture)} or {@link
 * Handler#exceptionCaught(HandlerContext, Exception)}) in {@link Handler} will not be invoked by
 * the {@link Pipeline} and so <strong>MUST</strong> only be used when the {@link Handler} method
 * does nothing except forward to the next {@link Handler} in the pipeline.
 * <p>
 * Note that this annotation is not {@linkplain Inherited inherited}. If a user overrides a method
 * annotated with {@link Skip}, it will not be skipped anymore. Similarly, the user can override a
 * method not annotated with {@link Skip} and simply pass the event through to the next handler,
 * which reverses the behavior of the supertype.
 * <b>The skippable method MUST be implemented always because some environments prohibit the
 * execution of reflections. In this case the method implementation is executed, also if it is
 * annotated with {@link Skip}</b>.
 * </p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Skip {
}
