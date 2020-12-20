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
package org.drasyl;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.crypto.CryptoException;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.event.PeerDirectEvent;
import org.drasyl.event.PeerEvent;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.Endpoint;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.awaitility.Awaitility.await;
import static org.drasyl.util.AnsiColor.COLOR_CYAN;
import static org.drasyl.util.AnsiColor.STYLE_REVERSED;
import static org.drasyl.util.NetworkUtil.createInetAddress;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static testutils.TestHelper.colorizedPrintln;

class DrasylNodeIT {
    public static final long TIMEOUT = 15000L;
    private List<DrasylNode> nodes;

    @BeforeEach
    void setup(final TestInfo info) {
        System.setProperty("io.netty.leakDetection.level", "PARANOID");

        colorizedPrintln("STARTING " + info.getDisplayName(), COLOR_CYAN, STYLE_REVERSED);
        nodes = new ArrayList<>();
    }

    @AfterEach
    void cleanUp(final TestInfo info) {
        nodes.forEach(n -> n.shutdown().join());
        colorizedPrintln("FINISHED " + info.getDisplayName(), COLOR_CYAN, STYLE_REVERSED);
    }

    private Pair<DrasylNode, Observable<Event>> createStartedNode(final DrasylConfig config) throws DrasylException {
        final Pair<DrasylNode, Observable<Event>> pair = createNode(config);
        pair.first().start();
        return pair;
    }

    private Pair<DrasylNode, Observable<Event>> createNode(final DrasylConfig config) throws DrasylException {
        final Subject<Event> subject = ReplaySubject.<Event>create().toSerialized();
        final DrasylNode node = new DrasylNode(config) {
            @Override
            public void onEvent(final Event event) {
                subject.onNext(event);
                if (event instanceof NodeNormalTerminationEvent) {
                    subject.onComplete();
                }
            }
        };
        nodes.add(node);

        return Pair.of(node, subject);
    }

    @Nested
    class TestRemote {
        /**
         * Network Layout:
         * <pre>
         *        +---+---+
         *        | Super |
         *        | Peer  |
         *        +-+--+--+
         *          |  |
         *     +----+  +-----+
         *     |             |
         * +---+----+   +----+---+
         * |Client 1|   |Client 2|
         * +--------+   +--------+
         * </pre>
         */
        @Nested
        class SuperPeerAndTwoClientWhenOnlyRemoteIsEnabled {
            private Pair<DrasylNode, Observable<Event>> superPeer;
            private Pair<DrasylNode, Observable<Event>> client1;
            private Pair<DrasylNode, Observable<Event>> client2;

            @BeforeEach
            void setUp() throws DrasylException, CryptoException {
                //
                // create nodes
                //
                DrasylConfig config;

                // super peer
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(ProofOfWork.of(6518542))
                        .identityPublicKey(CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"))
                        .identityPrivateKey(CompressedPrivateKey.of("6b4df6d8b8b509cb984508a681076efce774936c17cf450819e2262a9862f8"))
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("127.0.0.1"))
                        .remoteBindPort(0)
                        .remoteSuperPeerEnabled(false)
                        .intraVmDiscoveryEnabled(false)
                        .localHostDiscoveryEnabled(false)
                        .remoteMessageMtu(1024)
                        .build();
                superPeer = createStartedNode(config);
                final NodeEvent superPeerNodeUp = (NodeEvent) superPeer.second().filter(e -> e instanceof NodeUpEvent).firstElement().blockingGet();
                final int superPeerPort = superPeerNodeUp.getNode().getEndpoints().iterator().next().getPort();
                colorizedPrintln("CREATED superPeer", COLOR_CYAN, STYLE_REVERSED);

                // client1
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(ProofOfWork.of(12304070))
                        .identityPublicKey(CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4"))
                        .identityPrivateKey(CompressedPrivateKey.of("073a34ecaff06fdf3fbe44ddf3abeace43e3547033493b1ac4c0ae3c6ecd6173"))
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("127.0.0.1"))
                        .remoteBindPort(0)
                        .remotePingInterval(ofSeconds(1))
                        .remotePingTimeout(ofSeconds(2))
                        .remoteSuperPeerEndpoint(Endpoint.of("udp://127.0.0.1:" + superPeerPort + "#030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"))
                        .intraVmDiscoveryEnabled(false)
                        .localHostDiscoveryEnabled(false)
                        .remoteMessageMtu(1024)
                        .build();
                client1 = createStartedNode(config);
                colorizedPrintln("CREATED client1", COLOR_CYAN, STYLE_REVERSED);

                // client2
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(ProofOfWork.of(33957767))
                        .identityPublicKey(CompressedPublicKey.of("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e"))
                        .identityPrivateKey(CompressedPrivateKey.of("0310991def7b530fced318876ac71025ebc0449a95967a0efc2e423086198f54"))
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("127.0.0.1"))
                        .remoteBindPort(0)
                        .remotePingInterval(ofSeconds(1))
                        .remotePingTimeout(ofSeconds(2))
                        .remoteSuperPeerEndpoint(Endpoint.of("udp://127.0.0.1:" + superPeerPort + "#030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"))
                        .intraVmDiscoveryEnabled(false)
                        .localHostDiscoveryEnabled(false)
                        .remoteMessageMtu(1024)
                        .build();
                client2 = createStartedNode(config);
                colorizedPrintln("CREATED client2", COLOR_CYAN, STYLE_REVERSED);

                superPeer.second().filter(e -> e instanceof NodeUpEvent || e instanceof PeerDirectEvent).test().awaitCount(3).assertValueCount(3);
                client1.second().filter(e -> e instanceof NodeOnlineEvent || e instanceof PeerDirectEvent).test().awaitCount(2).assertValueCount(2);
                client2.second().filter(e -> e instanceof NodeOnlineEvent || e instanceof PeerDirectEvent).test().awaitCount(2).assertValueCount(2);
            }

            /**
             * This test ensures that sent application messages are delivered to the recipient
             * (either directly or relayed via super peer or a child). All nodes send messages to
             * every other node (including themselves). At the end, a check is made to ensure that
             * all nodes have received all messages.
             */
            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void applicationMessagesShouldBeDelivered() {
                final TestObserver<Event> superPeerMessages = superPeer.second().filter(e -> e instanceof MessageEvent).test();
                final TestObserver<Event> client1Messages = client1.second().filter(e -> e instanceof MessageEvent).test();
                final TestObserver<Event> client2Messages = client2.second().filter(e -> e instanceof MessageEvent).test();

//        superPeer.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("SP: " + e));
//        client1.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("C1: " + e));
//        client2.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("C2: " + e));

                //
                // send messages
                //
                final Set<String> identities = Set.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22",
                        "025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4",
                        "025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e");
                for (final String recipient : identities) {
                    superPeer.first().send(recipient, "Hallo Welt");
                    client1.first().send(recipient, "Hallo Welt");
                    client2.first().send(recipient, "Hallo Welt");
                }

                //
                // verify
                //
                superPeerMessages.awaitCount(3).assertValueCount(3);
                client1Messages.awaitCount(3).assertValueCount(3);
                client2Messages.awaitCount(3).assertValueCount(3);
            }

            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void applicationMessagesExceedingMtuShouldBeDelivered() {
                final TestObserver<Event> superPeerMessages = superPeer.second().filter(e -> e instanceof MessageEvent).test();
                final TestObserver<Event> client1Messages = client1.second().filter(e -> e instanceof MessageEvent).test();
                final TestObserver<Event> client2Messages = client2.second().filter(e -> e instanceof MessageEvent).test();

//        superPeer.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("SP: " + e));
//        client1.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("C1: " + e));
//        client2.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("C2: " + e));

                //
                // send messages
                //
                final Set<String> identities = Set.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22",
                        "025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4",
                        "025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e");
                for (final String recipient : identities) {
                    superPeer.first().send(recipient, new byte[1024]);
                    client1.first().send(recipient, new byte[1024]);
                    client2.first().send(recipient, new byte[1024]);
                }

                //
                // verify
                //
                superPeerMessages.awaitCount(3).assertValueCount(3);
                client1Messages.awaitCount(3).assertValueCount(3);
                client2Messages.awaitCount(3).assertValueCount(3);
            }

            /**
             * This test checks whether the correct {@link PeerEvent}s are emitted in the correct
             * order.
             */
            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void correctPeerEventsShouldBeEmitted() {
                //
                // send messages
                //
                final TestObserver<Event> superPeerEvents = superPeer.second().filter(e -> e instanceof PeerDirectEvent).test();
                final TestObserver<Event> client1Events = client1.second().filter(e -> e instanceof PeerDirectEvent).test();
                final TestObserver<Event> client2Events = client2.second().filter(e -> e instanceof PeerDirectEvent).test();

//            superPeer.second().subscribe(e -> System.err.println("SP: " + e));
//            client1.second().subscribe(e -> System.err.println("C1: " + e));
//            client2.second().subscribe(e -> System.err.println("C2: " + e));

                superPeerEvents.awaitCount(2).assertValueCount(2);
                client1Events.awaitCount(1).assertValueCount(1);
                client2Events.awaitCount(1).assertValueCount(1);
            }

            /**
             * This test checks whether the correct {@link PeerEvent}s are sent out by the other
             * nodes when a node is shut down
             */
            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void shuttingDownNodeShouldCloseConnections() {
                //
                // send messages
                //
                final TestObserver<Event> client1Events = client1.second().filter(e -> e instanceof NodeOfflineEvent).test();
                final TestObserver<Event> client2Events = client2.second().filter(e -> e instanceof NodeOfflineEvent).test();

                superPeer.first().shutdown().join();

                client1Events.awaitCount(1).assertValueCount(1);
                client2Events.awaitCount(1).assertValueCount(1);
            }
        }
    }

    @Nested
    class TestIntraVmDiscovery {
        /**
         * Network Layout:
         * <pre>
         * +---+----+   +----+---+   +----+---+   +----+---+
         * | Node 1 |   | Node 2 |   | Node 3 |   | Node 4 |
         * +--------+   +--------+   +----+---+   +----+---+
         * </pre>
         */
        @Nested
        class FourNodesWithOnlyIntraVmDiscoverIsEnabled {
            private Pair<DrasylNode, Observable<Event>> node1;
            private Pair<DrasylNode, Observable<Event>> node2;
            private Pair<DrasylNode, Observable<Event>> node3;
            private Pair<DrasylNode, Observable<Event>> node4;

            @BeforeEach
            void setUp() throws DrasylException, CryptoException {
                //
                // create nodes
                //
                DrasylConfig config;

                // node1
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(ProofOfWork.of(13290399))
                        .identityPublicKey(CompressedPublicKey.of("03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a"))
                        .identityPrivateKey(CompressedPrivateKey.of("0c2945e523e1ab27c3b38ba62f0a67a21567dcfcbad4ff3fe7f8f7b202a18c93"))
                        .remoteExposeEnabled(false)
                        .remoteEnabled(false)
                        .remoteSuperPeerEnabled(false)
                        .localHostDiscoveryEnabled(false)
                        .build();
                node1 = createStartedNode(config);
                colorizedPrintln("CREATED node1", COLOR_CYAN, STYLE_REVERSED);

                // node2
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(ProofOfWork.of(6518542))
                        .identityPublicKey(CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"))
                        .identityPrivateKey(CompressedPrivateKey.of("6b4df6d8b8b509cb984508a681076efce774936c17cf450819e2262a9862f8"))
                        .remoteExposeEnabled(false)
                        .remoteEnabled(false)
                        .remoteSuperPeerEnabled(false)
                        .localHostDiscoveryEnabled(false)
                        .build();
                node2 = createStartedNode(config);
                colorizedPrintln("CREATED node2", COLOR_CYAN, STYLE_REVERSED);

                // node3
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(ProofOfWork.of(12304070))
                        .identityPublicKey(CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4"))
                        .identityPrivateKey(CompressedPrivateKey.of("073a34ecaff06fdf3fbe44ddf3abeace43e3547033493b1ac4c0ae3c6ecd6173"))
                        .remoteExposeEnabled(false)
                        .remoteEnabled(false)
                        .remoteSuperPeerEnabled(false)
                        .localHostDiscoveryEnabled(false)
                        .build();
                node3 = createStartedNode(config);
                colorizedPrintln("CREATED node3", COLOR_CYAN, STYLE_REVERSED);

                // node4
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(ProofOfWork.of(33957767))
                        .identityPublicKey(CompressedPublicKey.of("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e"))
                        .identityPrivateKey(CompressedPrivateKey.of("0310991def7b530fced318876ac71025ebc0449a95967a0efc2e423086198f54"))
                        .remoteExposeEnabled(false)
                        .remoteEnabled(false)
                        .remoteSuperPeerEnabled(false)
                        .localHostDiscoveryEnabled(false)
                        .build();
                node4 = createStartedNode(config);
                colorizedPrintln("CREATED node4", COLOR_CYAN, STYLE_REVERSED);

                node1.second().filter(e -> e instanceof NodeUpEvent).test().awaitCount(1).assertValueCount(1);
                node2.second().filter(e -> e instanceof NodeUpEvent).test().awaitCount(1).assertValueCount(1);
                node3.second().filter(e -> e instanceof NodeUpEvent).test().awaitCount(1).assertValueCount(1);
                node4.second().filter(e -> e instanceof NodeUpEvent).test().awaitCount(1).assertValueCount(1);
            }

            /**
             * This test checks whether the messages sent via {@link org.drasyl.intravm.IntraVmDiscovery}
             * are delivered.
             */
            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void applicationMessagesShouldBeDelivered() {
                node1.second().filter(e -> e instanceof PeerDirectEvent).test().awaitCount(3).assertValueCount(3);
                node2.second().filter(e -> e instanceof PeerDirectEvent).test().awaitCount(3).assertValueCount(3);
                node3.second().filter(e -> e instanceof PeerDirectEvent).test().awaitCount(3).assertValueCount(3);
                node4.second().filter(e -> e instanceof PeerDirectEvent).test().awaitCount(3).assertValueCount(3);

                final TestObserver<Event> node1Messages = node1.second().filter(e -> e instanceof MessageEvent).test();
                final TestObserver<Event> nodes2Messages = node2.second().filter(e -> e instanceof MessageEvent).test();
                final TestObserver<Event> node3Messages = node3.second().filter(e -> e instanceof MessageEvent).test();
                final TestObserver<Event> node4Messages = node4.second().filter(e -> e instanceof MessageEvent).test();

//        superPeer.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("SSP: " + e));
//        superPeer.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("SP: " + e));
//        client1.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("C1: " + e));
//        client2.second().filter(e -> e.getCode() == MESSAGE).subscribe(e -> System.err.println("C2: " + e));

                //
                // send messages
                //
                final Set<String> identities = Set.of("03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a",
                        "030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22",
                        "025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4",
                        "025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e");
                for (final String recipient : identities) {
                    node1.first().send(recipient, "Hallo Welt");
                    node2.first().send(recipient, "Hallo Welt");
                    node3.first().send(recipient, "Hallo Welt");
                    node4.first().send(recipient, "Hallo Welt");
                }

                //
                // verify
                //
                node1Messages.awaitCount(4).assertValueCount(4);
                nodes2Messages.awaitCount(4).assertValueCount(4);
                node3Messages.awaitCount(4).assertValueCount(4);
                node4Messages.awaitCount(4).assertValueCount(4);
            }

            /**
             * This test checks whether the {@link org.drasyl.intravm.IntraVmDiscovery} emits the
             * correct {@link PeerEvent}s.
             */
            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void correctPeerEventsShouldBeEmitted() {
                final TestObserver<Event> node1Events = node1.second().filter(e -> e instanceof PeerDirectEvent).test();
                final TestObserver<Event> node2Events = node2.second().filter(e -> e instanceof PeerDirectEvent).test();
                final TestObserver<Event> node3Events = node3.second().filter(e -> e instanceof PeerDirectEvent).test();
                final TestObserver<Event> node4Events = node4.second().filter(e -> e instanceof PeerDirectEvent).test();

                node1Events.awaitCount(3).assertValueCount(3);
                node2Events.awaitCount(3).assertValueCount(3);
                node3Events.awaitCount(3).assertValueCount(3);
                node4Events.awaitCount(3).assertValueCount(3);
            }
        }
    }

    @Nested
    class TestLocalHostDiscovery {
        /**
         * Network Layout:
         * <pre>
         * +---+----+   +----+---+   +----+---+   +----+---+
         * | Node 1 |   | Node 2 |   | Node 3 |   | Node 4 |
         * +--------+   +--------+   +----+---+   +----+---+
         * </pre>
         */
        @Nested
        class FourNodesWithOnlyLocalHostDiscoveryEnabled {
            private Pair<DrasylNode, Observable<Event>> node1;
            private Pair<DrasylNode, Observable<Event>> node2;
            private Pair<DrasylNode, Observable<Event>> node3;
            private Pair<DrasylNode, Observable<Event>> node4;

            @BeforeEach
            void setUp() throws DrasylException, CryptoException {
                //
                // create nodes
                //
                DrasylConfig config;

                // node1
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(ProofOfWork.of(13290399))
                        .identityPublicKey(CompressedPublicKey.of("03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a"))
                        .identityPrivateKey(CompressedPrivateKey.of("0c2945e523e1ab27c3b38ba62f0a67a21567dcfcbad4ff3fe7f8f7b202a18c93"))
                        .remoteExposeEnabled(false)
                        .remoteEnabled(true)
                        .remoteBindPort(0)
                        .remoteSuperPeerEnabled(false)
                        .intraVmDiscoveryEnabled(false)
                        .build();
                node1 = createStartedNode(config);
                colorizedPrintln("CREATED node1", COLOR_CYAN, STYLE_REVERSED);

                // node2
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(ProofOfWork.of(6518542))
                        .identityPublicKey(CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"))
                        .identityPrivateKey(CompressedPrivateKey.of("6b4df6d8b8b509cb984508a681076efce774936c17cf450819e2262a9862f8"))
                        .remoteExposeEnabled(false)
                        .remoteEnabled(true)
                        .remoteBindPort(0)
                        .remoteSuperPeerEnabled(false)
                        .intraVmDiscoveryEnabled(false)
                        .build();
                node2 = createStartedNode(config);
                colorizedPrintln("CREATED node2", COLOR_CYAN, STYLE_REVERSED);

                // node3
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(ProofOfWork.of(12304070))
                        .identityPublicKey(CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4"))
                        .identityPrivateKey(CompressedPrivateKey.of("073a34ecaff06fdf3fbe44ddf3abeace43e3547033493b1ac4c0ae3c6ecd6173"))
                        .remoteExposeEnabled(false)
                        .remoteEnabled(true)
                        .remoteBindPort(0)
                        .remoteSuperPeerEnabled(false)
                        .intraVmDiscoveryEnabled(false)
                        .build();
                node3 = createStartedNode(config);
                colorizedPrintln("CREATED node3", COLOR_CYAN, STYLE_REVERSED);

                // node4
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(ProofOfWork.of(33957767))
                        .identityPublicKey(CompressedPublicKey.of("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e"))
                        .identityPrivateKey(CompressedPrivateKey.of("0310991def7b530fced318876ac71025ebc0449a95967a0efc2e423086198f54"))
                        .remoteExposeEnabled(false)
                        .remoteEnabled(true)
                        .remoteBindPort(0)
                        .remoteSuperPeerEnabled(false)
                        .intraVmDiscoveryEnabled(false)
                        .build();
                node4 = createStartedNode(config);
                colorizedPrintln("CREATED node4", COLOR_CYAN, STYLE_REVERSED);

                node1.second().filter(e -> e instanceof NodeUpEvent).test().awaitCount(1).assertValueCount(1);
                node2.second().filter(e -> e instanceof NodeUpEvent).test().awaitCount(1).assertValueCount(1);
                node3.second().filter(e -> e instanceof NodeUpEvent).test().awaitCount(1).assertValueCount(1);
                node4.second().filter(e -> e instanceof NodeUpEvent).test().awaitCount(1).assertValueCount(1);
            }

            /**
             * This test checks whether the {@link org.drasyl.localhost.LocalHostDiscovery} emits
             * the correct {@link PeerEvent}s after communication occurred.
             */
            @Disabled("Fails in CI for unknown reasons")
            @Test
            @Timeout(value = TIMEOUT * 2, unit = MILLISECONDS)
            void correctPeerEventsShouldBeEmitted() {
                /*
                 * TODO: Fix this test by using the PeerDirectEvent.
                 * Therefore we need a PeerInformation onChange listener in the PeersManager.
                 */
                final TestObserver<Event> node1Events = node1.second().filter(e -> e instanceof PeerDirectEvent).test();
                final TestObserver<Event> node2Events = node2.second().filter(e -> e instanceof PeerDirectEvent).test();
                final TestObserver<Event> node3Events = node3.second().filter(e -> e instanceof PeerDirectEvent).test();
                final TestObserver<Event> node4Events = node4.second().filter(e -> e instanceof PeerDirectEvent).test();

                await().atMost(ofSeconds(60)).until(() -> {
                    // since LocalHostDiscovery only performs a discovery on communication, we have to simulate a constant communication
                    node1.first().send("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22", "Hallo Welt");
                    node2.first().send("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4", "Hallo Welt");
                    node3.first().send("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e", "Hallo Welt");
                    node4.first().send("03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a", "Hallo Welt");

                    // here we check if the other three peers were found by LocalHostDiscovery
                    return node1Events.values().size() == 3 && node2Events.values().size() == 3 && node3Events.values().size() == 3 && node4Events.values().size() == 3;
                });
            }
        }
    }

    /**
     * Network Layout:
     * <pre>
     * +---+----+
     * | Node 1 |
     * +--------+
     * </pre>
     */
    @Nested
    class OneNodeWithNoDiscoveryMethodsEnabled {
        private Pair<DrasylNode, Observable<Event>> node1;

        @BeforeEach
        void setUp() throws DrasylException, CryptoException {
            //
            // create nodes
            //
            final DrasylConfig config;

            // node1
            config = DrasylConfig.newBuilder()
                    .networkId(0)
                    .identityProofOfWork(ProofOfWork.of(12304070))
                    .identityPublicKey(CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4"))
                    .identityPrivateKey(CompressedPrivateKey.of("073a34ecaff06fdf3fbe44ddf3abeace43e3547033493b1ac4c0ae3c6ecd6173"))
                    .remoteExposeEnabled(false)
                    .remoteEnabled(false)
                    .remoteSuperPeerEnabled(false)
                    .intraVmDiscoveryEnabled(false)
                    .localHostDiscoveryEnabled(false)
                    .build();
            node1 = createStartedNode(config);
            node1.second().filter(e -> e instanceof NodeUpEvent).test().awaitCount(1).assertValueCount(1);
            colorizedPrintln("CREATED node1", COLOR_CYAN, STYLE_REVERSED);
        }

        /**
         * This test ensures that loopback message discovery work.
         */
        @Test
        @Timeout(value = TIMEOUT, unit = MILLISECONDS)
        void applicationMessagesShouldBeDelivered() {
            final TestObserver<Event> node1Messages = node1.second().filter(e -> e instanceof MessageEvent).test();

            node1.first().send("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4", "Hallo Welt");

            node1Messages.awaitCount(1).assertValueCount(1);
        }
    }

    @Nested
    class Send {
        /**
         * Network Layout:
         * <pre>
         * +---+----+
         * | Node 1 |
         * +--------+
         * </pre>
         * Non-started
         */
        @Nested
        class SingleNonStartedNode {
            private Pair<DrasylNode, Observable<Event>> node1;

            @BeforeEach
            void setUp() throws DrasylException, CryptoException {
                //
                // create nodes
                //
                final DrasylConfig config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(ProofOfWork.of(33957767))
                        .identityPublicKey(CompressedPublicKey.of("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e"))
                        .identityPrivateKey(CompressedPrivateKey.of("0310991def7b530fced318876ac71025ebc0449a95967a0efc2e423086198f54"))
                        .remoteExposeEnabled(false)
                        .remoteEnabled(false)
                        .remoteSuperPeerEnabled(false)
                        .localHostDiscoveryEnabled(false)
                        .build();
                node1 = createNode(config);
                colorizedPrintln("CREATED node1", COLOR_CYAN, STYLE_REVERSED);
            }

            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void sendToSelfShouldThrowException() {
                assertThrows(ExecutionException.class, () -> node1.first().send("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e", "Hallo Welt").get());
            }

            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void sendToAnOtherPeerShouldThrowException() {
                assertThrows(ExecutionException.class, () -> node1.first().send("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22", "Hallo Welt").get());
            }
        }
    }
}