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
package org.drasyl.peer;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import org.drasyl.event.Event;
import org.drasyl.event.Node;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.event.Peer;
import org.drasyl.event.PeerDirectEvent;
import org.drasyl.event.PeerRelayEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.util.SetUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * This class contains information about other peers. This includes the public keys, available
 * interfaces, connections or relations (e.g. direct/relayed connection, super peer, child). Before
 * a relation is set for a peer, it must be ensured that its information is available. Likewise, the
 * information may not be removed from a peer if the peer still has a relation
 *
 * <p>
 * This class is optimized for concurrent access and is thread-safe.
 * </p>
 */
public class PeersManager {
    private final ReadWriteLock lock;
    private final SetMultimap<CompressedPublicKey, Object> paths;
    private final Set<CompressedPublicKey> children;
    private final Consumer<Event> eventConsumer;
    private final Identity identity;
    private final Set<CompressedPublicKey> superPeers;

    public PeersManager(final Consumer<Event> eventConsumer, final Identity identity) {
        this(new ReentrantReadWriteLock(true), HashMultimap.create(), new HashSet<>(), new HashSet<>(), eventConsumer, identity);
    }

    @SuppressWarnings("java:S2384")
    PeersManager(final ReadWriteLock lock,
                 final SetMultimap<CompressedPublicKey, Object> paths,
                 final Set<CompressedPublicKey> children,
                 final Set<CompressedPublicKey> superPeers,
                 final Consumer<Event> eventConsumer,
                 final Identity identity) {
        this.lock = lock;
        this.paths = paths;
        this.children = children;
        this.superPeers = superPeers;
        this.eventConsumer = eventConsumer;
        this.identity = identity;
    }

    @Override
    public String toString() {
        try {
            lock.readLock().lock();

            return "PeersManager{" +
                    ", paths=" + paths +
                    ", children=" + children +
                    ", eventConsumer=" + eventConsumer +
                    ", superPeers=" + superPeers +
                    '}';
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Set<CompressedPublicKey> getPeers() {
        try {
            lock.readLock().lock();

            return SetUtil.merge(paths.keySet(), SetUtil.merge(superPeers, children));
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Set<CompressedPublicKey> getChildren() {
        try {
            lock.readLock().lock();

            // It is necessary to create a new HashMap because otherwise, this can raise a
            // ConcurrentModificationException.
            // See: https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/issues/77
            return Set.copyOf(children);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Set<CompressedPublicKey> getSuperPeers() {
        try {
            lock.readLock().lock();

            // It is necessary to create a new HashMap because otherwise, this can raise a
            // ConcurrentModificationException.
            // See: https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/issues/77
            return Set.copyOf(superPeers);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Set<Object> getPaths(final CompressedPublicKey publicKey) {
        requireNonNull(publicKey);

        try {
            lock.readLock().lock();

            return Set.copyOf(paths.get(publicKey));
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public void addPath(final CompressedPublicKey publicKey,
                        final Object path) {
        requireNonNull(publicKey);

        try {
            lock.writeLock().lock();

            final boolean firstPath = paths.get(publicKey).isEmpty();
            if (paths.put(publicKey, path) && firstPath) {
                eventConsumer.accept(PeerDirectEvent.of(Peer.of(publicKey)));
            }

        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void removePath(final CompressedPublicKey publicKey, final Object path) {
        requireNonNull(publicKey);
        requireNonNull(path);

        try {
            lock.writeLock().lock();

            if (paths.remove(publicKey, path) && paths.get(publicKey).isEmpty()) {
                eventConsumer.accept(PeerRelayEvent.of(Peer.of(publicKey)));
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void addPathAndSuperPeer(final CompressedPublicKey publicKey,
                                    final Object path) {
        requireNonNull(publicKey);
        requireNonNull(path);

        try {
            lock.writeLock().lock();

            // path
            final boolean firstPath = paths.get(publicKey).isEmpty();
            if (paths.put(publicKey, path) && firstPath) {
                eventConsumer.accept(PeerDirectEvent.of(Peer.of(publicKey)));
            }

            // role (super peer)
            final boolean firstSuperPeer = superPeers.isEmpty();
            if (superPeers.add(publicKey) && firstSuperPeer) {
                eventConsumer.accept(NodeOnlineEvent.of(Node.of(identity)));
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void removeSuperPeerAndPath(final CompressedPublicKey publicKey,
                                       final Object path) {
        requireNonNull(path);

        try {
            lock.writeLock().lock();

            // role (super peer)
            if (superPeers.remove(publicKey) && superPeers.isEmpty()) {
                eventConsumer.accept(NodeOfflineEvent.of(Node.of(identity)));
            }

            // path
            if (paths.remove(publicKey, path) && paths.get(publicKey).isEmpty()) {
                eventConsumer.accept(PeerRelayEvent.of(Peer.of(publicKey)));
            }
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void addPathAndChildren(final CompressedPublicKey publicKey,
                                   final Object path) {
        requireNonNull(publicKey);
        requireNonNull(path);

        try {
            lock.writeLock().lock();

            // path
            final boolean firstPath = paths.get(publicKey).isEmpty();
            if (paths.put(publicKey, path) && firstPath) {
                eventConsumer.accept(PeerDirectEvent.of(Peer.of(publicKey)));
            }

            // role (children peer)
            children.add(publicKey);
        }
        finally {
            lock.writeLock().unlock();
        }
    }

    public void removeChildrenAndPath(final CompressedPublicKey publicKey,
                                      final Object path) {
        requireNonNull(publicKey);
        requireNonNull(path);

        try {
            lock.writeLock().lock();

            // path
            if (paths.remove(publicKey, path) && paths.get(publicKey).isEmpty()) {
                eventConsumer.accept(PeerRelayEvent.of(Peer.of(publicKey)));
            }

            // role (children)
            children.remove(publicKey);
        }
        finally {
            lock.writeLock().unlock();
        }
    }
}
