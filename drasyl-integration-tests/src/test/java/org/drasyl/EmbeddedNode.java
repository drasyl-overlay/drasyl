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
package org.drasyl;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.annotation.NonNull;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;

import java.io.Closeable;
import java.util.Arrays;

import static java.util.Objects.requireNonNull;

public class EmbeddedNode extends DrasylNode implements Closeable {
    private final Subject<Event> events;
    private int port;

    private EmbeddedNode(final DrasylConfig config,
                         final Subject<Event> events) throws DrasylException {
        super(config);
        this.events = requireNonNull(events);
    }

    public EmbeddedNode(final DrasylConfig config) throws DrasylException {
        this(config, ReplaySubject.<Event>create().toSerialized());
    }

    @Override
    public void onEvent(@NonNull final Event event) {
        if (event instanceof NodeUnrecoverableErrorEvent) {
            events.onError(((NodeUnrecoverableErrorEvent) event).getError());
        }
        else {
            if (event instanceof NodeUpEvent) {
                port = ((NodeUpEvent) event).getNode().getPort();
            }

            events.onNext(event);

            if (event instanceof NodeNormalTerminationEvent) {
                events.onComplete();
            }
        }
    }

    @SuppressWarnings("unused")
    public Observable<Event> events() {
        return events;
    }

    @SuppressWarnings("unchecked")
    public <T extends Event> Observable<T> events(final Class<T> clazz) {
        return (Observable<T>) events.filter(clazz::isInstance);
    }

    @SuppressWarnings("unchecked")
    public Observable<Event> events(final Class<? extends Event>... clazzes) {
        return events.filter(event -> Arrays.stream(clazzes).anyMatch(clazz -> clazz.isInstance(event)));
    }

    public Observable<MessageEvent> messages() {
        return events(MessageEvent.class);
    }

    public EmbeddedNode started() {
        start();
        events(NodeUpEvent.class).test().awaitCount(1).assertValueCount(1);
        return this;
    }

    public EmbeddedNode online() {
        events(NodeOnlineEvent.class).test().awaitCount(1).assertValueCount(1);
        return this;
    }

    @Override
    public void close() {
        shutdown().join();
    }

    public int getPort() {
        if (port == 0) {
            throw new IllegalStateException("Port not set. You have to start the node first.");
        }
        return port;
    }
}
