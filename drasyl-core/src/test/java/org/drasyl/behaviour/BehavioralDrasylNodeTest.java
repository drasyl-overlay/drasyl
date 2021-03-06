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
package org.drasyl.behaviour;

import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.drasyl.behaviour.Behavior.SAME;
import static org.drasyl.behaviour.Behavior.SHUTDOWN;
import static org.drasyl.behaviour.Behavior.UNHANDLED;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BehavioralDrasylNodeTest {
    private final AtomicReference<CompletableFuture<Void>> startFuture = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<Void>> shutdownFuture = new AtomicReference<>();
    @Mock
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private PeersManager peersManager;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Pipeline pipeline;
    @Mock
    private PluginManager pluginManager;
    @Mock
    private Scheduler scheduler;
    @Mock
    private Behavior behavior;

    @Nested
    class OnEvent {
        private BehavioralDrasylNode node;

        @BeforeEach
        void setUp() {
            node = spy(new BehavioralDrasylNode(config, identity, peersManager, pipeline, pluginManager, startFuture, shutdownFuture, scheduler, behavior) {
                @Override
                protected Behavior created() {
                    return null;
                }
            });
        }

        @Test
        void shouldPassEventToBehavior(@Mock final Event event) {
            node.onEvent(event);

            verify(behavior).receive(event);
        }

        @Test
        void shouldSwitchToNewBehavior(@Mock final Event event,
                                       @Mock final Behavior newBehavior) {
            when(behavior.receive(any())).thenReturn(newBehavior);

            node.onEvent(event);

            assertSame(newBehavior, node.behavior);
        }

        @Test
        void shouldUnpackDeferredBehavior(@Mock final Event event,
                                          @Mock final DeferredBehavior deferredBehavior) {
            when(behavior.receive(any())).thenReturn(deferredBehavior);

            node.onEvent(event);

            verify(deferredBehavior).apply(node);
        }

        @Test
        void shouldStayOnSameBehaviorIfNewBehaviorIsSame(@Mock final Event event) {
            when(behavior.receive(any())).thenReturn(SAME);

            node.onEvent(event);

            assertSame(behavior, node.behavior);
        }

        @Test
        void shouldStayOnSameBehaviorIfNewBehaviorIsUnhandled(@Mock final Event event) {
            when(behavior.receive(any())).thenReturn(UNHANDLED);

            node.onEvent(event);

            assertSame(behavior, node.behavior);
        }

        @Test
        void shouldShutdownNodeIfNewBehaviorIsShutdown(@Mock final Event event) {
            when(behavior.receive(any())).thenReturn(SHUTDOWN);

            node.onEvent(event);

            verify(node).shutdown();
            assertSame(SHUTDOWN, node.behavior);
        }
    }
}
