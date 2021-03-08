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

import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.PeerDirectEvent;
import org.drasyl.event.PeerEvent;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.Endpoint;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.util.RandomUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.AnsiColor.COLOR_CYAN;
import static org.drasyl.util.AnsiColor.STYLE_REVERSED;
import static org.drasyl.util.NetworkUtil.createInetAddress;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static testutils.TestHelper.colorizedPrintln;

class DrasylNodeIT {
    public static final long TIMEOUT = 15000L;
    public static final int MESSAGE_MTU = 1024;

    @BeforeEach
    void setup(final TestInfo info) {
        System.setProperty("io.netty.leakDetection.level", "PARANOID");

        colorizedPrintln("STARTING " + info.getDisplayName(), COLOR_CYAN, STYLE_REVERSED);
    }

    @AfterEach
    void cleanUp(final TestInfo info) {
        colorizedPrintln("FINISHED " + info.getDisplayName(), COLOR_CYAN, STYLE_REVERSED);
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
            private EmbeddedNode superPeer;
            private EmbeddedNode client1;
            private EmbeddedNode client2;

            @BeforeEach
            void setUp() throws DrasylException {
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
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteMessageMtu(MESSAGE_MTU)
                        .build();
                superPeer = new EmbeddedNode(config).started();
                colorizedPrintln("CREATED superPeer", COLOR_CYAN, STYLE_REVERSED);

                // client1
                System.err.println(superPeer.getPort());
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
                        .remoteSuperPeerEndpoints(Set.of(Endpoint.of("udp://127.0.0.1:" + superPeer.getPort() + "?publicKey=030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22")))
                        .intraVmDiscoveryEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteMessageMtu(MESSAGE_MTU)
                        .build();
                client1 = new EmbeddedNode(config).started().online();
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
                        .remoteSuperPeerEndpoints(Set.of(Endpoint.of("udp://127.0.0.1:" + superPeer.getPort() + "?publicKey=030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22")))
                        .intraVmDiscoveryEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteMessageMtu(MESSAGE_MTU)
                        .build();
                client2 = new EmbeddedNode(config).started().online();
                colorizedPrintln("CREATED client2", COLOR_CYAN, STYLE_REVERSED);

                superPeer.events(PeerDirectEvent.class).test().awaitCount(2).assertValueCount(2);
                client1.events(PeerDirectEvent.class).test().awaitCount(1).assertValueCount(1);
                client2.events(PeerDirectEvent.class).test().awaitCount(1).assertValueCount(1);
            }

            @AfterEach
            void tearDown() {
                superPeer.close();
                client1.close();
                client2.close();
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
                final TestObserver<MessageEvent> superPeerMessages = superPeer.messages().test();
                final TestObserver<MessageEvent> client1Messages = client1.messages().test();
                final TestObserver<MessageEvent> client2Messages = client2.messages().test();

                //
                // send messages
                //
                final Set<String> identities = Set.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22",
                        "025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4",
                        "025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e");
                for (final String recipient : identities) {
                    superPeer.send(recipient, "Hallo Welt");
                    client1.send(recipient, "Hallo Welt");
                    client2.send(recipient, "Hallo Welt");
                }

                //
                // verify
                //
                superPeerMessages.awaitCount(3).assertValueCount(3)
                        .assertValueAt(0, m -> m.getPayload().equals("Hallo Welt"))
                        .assertValueAt(1, m -> m.getPayload().equals("Hallo Welt"))
                        .assertValueAt(2, m -> m.getPayload().equals("Hallo Welt"));
                client1Messages.awaitCount(3).assertValueCount(3)
                        .assertValueAt(0, m -> m.getPayload().equals("Hallo Welt"))
                        .assertValueAt(1, m -> m.getPayload().equals("Hallo Welt"))
                        .assertValueAt(2, m -> m.getPayload().equals("Hallo Welt"));
                client2Messages.awaitCount(3).assertValueCount(3)
                        .assertValueAt(0, m -> m.getPayload().equals("Hallo Welt"))
                        .assertValueAt(1, m -> m.getPayload().equals("Hallo Welt"))
                        .assertValueAt(2, m -> m.getPayload().equals("Hallo Welt"));
            }

            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void applicationMessagesExceedingMtuShouldBeDelivered() {
                final TestObserver<MessageEvent> superPeerMessages = superPeer.messages().test();
                final TestObserver<MessageEvent> client1Messages = client1.messages().test();
                final TestObserver<MessageEvent> client2Messages = client2.messages().test();

                //
                // send messages
                //
                final byte[] payload = RandomUtil.randomBytes(MESSAGE_MTU);
                final Set<String> identities = Set.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22",
                        "025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4",
                        "025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e");
                for (final String recipient : identities) {
                    superPeer.send(recipient, payload);
                    client1.send(recipient, payload);
                    client2.send(recipient, payload);
                }

                //
                // verify
                //
                superPeerMessages.awaitCount(3).assertValueCount(3)
                        .assertValueAt(0, m -> Objects.deepEquals(m.getPayload(), payload))
                        .assertValueAt(1, m -> Objects.deepEquals(m.getPayload(), payload))
                        .assertValueAt(2, m -> Objects.deepEquals(m.getPayload(), payload));
                client1Messages.awaitCount(3).assertValueCount(3)
                        .assertValueAt(0, m -> Objects.deepEquals(m.getPayload(), payload))
                        .assertValueAt(1, m -> Objects.deepEquals(m.getPayload(), payload))
                        .assertValueAt(2, m -> Objects.deepEquals(m.getPayload(), payload));
                client2Messages.awaitCount(3).assertValueCount(3)
                        .assertValueAt(0, m -> Objects.deepEquals(m.getPayload(), payload))
                        .assertValueAt(1, m -> Objects.deepEquals(m.getPayload(), payload))
                        .assertValueAt(2, m -> Objects.deepEquals(m.getPayload(), payload));
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
                final TestObserver<PeerDirectEvent> superPeerEvents = superPeer.events(PeerDirectEvent.class).test();
                final TestObserver<PeerDirectEvent> client1Events = client1.events(PeerDirectEvent.class).test();
                final TestObserver<PeerDirectEvent> client2Events = client2.events(PeerDirectEvent.class).test();

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
                final TestObserver<NodeOfflineEvent> client1Events = client1.events(NodeOfflineEvent.class).test();
                final TestObserver<NodeOfflineEvent> client2Events = client2.events(NodeOfflineEvent.class).test();

                superPeer.shutdown().join();

                client1Events.awaitCount(1).assertValueCount(1);
                client2Events.awaitCount(1).assertValueCount(1);
            }
        }

        /**
         * Network Layout:
         * <pre>
         * +---+----+   +----+---+
         * |Client 1|   |Client 2|
         * +--------+   +--------+
         * </pre>
         */
        @Nested
        class TwoClientWithStaticRoutesAndWithoutSuperPeerWhenOnlyRemoteIsEnabled {
            private EmbeddedNode client1;
            private EmbeddedNode client2;

            @BeforeEach
            void setUp() throws DrasylException {
                //
                // create nodes
                //
                DrasylConfig config;

                // client1
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(ProofOfWork.of(12304070))
                        .identityPublicKey(CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4"))
                        .identityPrivateKey(CompressedPrivateKey.of("073a34ecaff06fdf3fbe44ddf3abeace43e3547033493b1ac4c0ae3c6ecd6173"))
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("127.0.0.1"))
                        .remoteBindPort(22528)
                        .remotePingInterval(ofSeconds(1))
                        .remotePingTimeout(ofSeconds(2))
                        .remoteSuperPeerEnabled(false)
                        .remoteStaticRoutes(Map.of(CompressedPublicKey.of("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e"), new InetSocketAddressWrapper("127.0.0.1", 22529)))
                        .intraVmDiscoveryEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteMessageMtu(MESSAGE_MTU)
                        .build();
                client1 = new EmbeddedNode(config).started();
                colorizedPrintln("CREATED client1", COLOR_CYAN, STYLE_REVERSED);

                // client2
                config = DrasylConfig.newBuilder()
                        .networkId(0)
                        .identityProofOfWork(ProofOfWork.of(33957767))
                        .identityPublicKey(CompressedPublicKey.of("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e"))
                        .identityPrivateKey(CompressedPrivateKey.of("0310991def7b530fced318876ac71025ebc0449a95967a0efc2e423086198f54"))
                        .remoteExposeEnabled(false)
                        .remoteBindHost(createInetAddress("127.0.0.1"))
                        .remoteBindPort(22529)
                        .remotePingInterval(ofSeconds(1))
                        .remotePingTimeout(ofSeconds(2))
                        .remoteSuperPeerEnabled(false)
                        .remoteStaticRoutes(Map.of(CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4"), new InetSocketAddressWrapper("127.0.0.1", 22528)))
                        .intraVmDiscoveryEnabled(false)
                        .remoteLocalHostDiscoveryEnabled(false)
                        .remoteMessageMtu(MESSAGE_MTU)
                        .build();
                client2 = new EmbeddedNode(config).started();
                colorizedPrintln("CREATED client2", COLOR_CYAN, STYLE_REVERSED);

                client1.events(PeerDirectEvent.class).test().awaitCount(1).assertValueCount(1);
                client2.events(PeerDirectEvent.class).test().awaitCount(1).assertValueCount(1);
            }

            @AfterEach
            void tearDown() {
                client1.close();
                client2.close();
            }

            /**
             * This test ensures that sent application messages are delivered to the recipient.
             */
            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void applicationMessagesShouldBeDelivered() throws ExecutionException, InterruptedException {
                final TestObserver<MessageEvent> client1Messages = client1.messages().test();
                final TestObserver<MessageEvent> client2Messages = client2.messages().test();

                //
                // send messages
                //
                client1.send("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e", true).get();
                client1.send("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e", (byte) 23).get();
                client1.send("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e", 'C').get();
                client1.send("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e", 3.141F).get();
                client1.send("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e", 1337).get();
                client1.send("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e", 9001L).get();
                client1.send("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e", (short) 42).get();
                client1.send("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e", new byte[]{
                        (byte) 0,
                        (byte) 1
                }).get();
                client1.send("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e", "String").get();
                client1.send("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e", null).get();

                client2.send("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4", true).get();
                client2.send("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4", (byte) 23).get();
                client2.send("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4", 'C').get();
                client2.send("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4", 3.141F).get();
                client2.send("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4", 1337).get();
                client2.send("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4", 9001L).get();
                client2.send("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4", (short) 42).get();
                client2.send("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4", new byte[]{
                        (byte) 0,
                        (byte) 1
                }).get();
                client2.send("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4", "String").get();
                client2.send("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4", null).get();

                //
                // verify
                //
                client1Messages.awaitCount(10).assertValueCount(10);
                client2Messages.awaitCount(10).assertValueCount(10);
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
            private EmbeddedNode node1;
            private EmbeddedNode node2;
            private EmbeddedNode node3;
            private EmbeddedNode node4;

            @BeforeEach
            void setUp() throws DrasylException {
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
                        .remoteLocalHostDiscoveryEnabled(false)
                        .build();
                node1 = new EmbeddedNode(config).started();
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
                        .remoteLocalHostDiscoveryEnabled(false)
                        .build();
                node2 = new EmbeddedNode(config).started();
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
                        .remoteLocalHostDiscoveryEnabled(false)
                        .build();
                node3 = new EmbeddedNode(config).started();
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
                        .remoteLocalHostDiscoveryEnabled(false)
                        .build();
                node4 = new EmbeddedNode(config).started();
                colorizedPrintln("CREATED node4", COLOR_CYAN, STYLE_REVERSED);
            }

            @AfterEach
            void tearDown() {
                node1.close();
                node2.close();
                node3.close();
                node4.close();
            }

            /**
             * This test checks whether the messages sent via {@link org.drasyl.intravm.IntraVmDiscovery}
             * are delivered.
             */
            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void applicationMessagesShouldBeDelivered() {
                node1.events(PeerDirectEvent.class).test().awaitCount(3).assertValueCount(3);
                node2.events(PeerDirectEvent.class).test().awaitCount(3).assertValueCount(3);
                node3.events(PeerDirectEvent.class).test().awaitCount(3).assertValueCount(3);
                node4.events(PeerDirectEvent.class).test().awaitCount(3).assertValueCount(3);

                final TestObserver<MessageEvent> node1Messages = node1.messages().test();
                final TestObserver<MessageEvent> nodes2Messages = node2.messages().test();
                final TestObserver<MessageEvent> node3Messages = node3.messages().test();
                final TestObserver<MessageEvent> node4Messages = node4.messages().test();

                //
                // send messages
                //
                final Set<String> identities = Set.of("03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a",
                        "030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22",
                        "025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4",
                        "025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e");
                for (final String recipient : identities) {
                    node1.send(recipient, "Hallo Welt");
                    node2.send(recipient, "Hallo Welt");
                    node3.send(recipient, "Hallo Welt");
                    node4.send(recipient, "Hallo Welt");
                }

                //
                // verify
                //
                node1Messages.awaitCount(4).assertValueCount(4)
                        .assertValueAt(0, m -> m.getPayload().equals("Hallo Welt"))
                        .assertValueAt(1, m -> m.getPayload().equals("Hallo Welt"))
                        .assertValueAt(2, m -> m.getPayload().equals("Hallo Welt"))
                        .assertValueAt(3, m -> m.getPayload().equals("Hallo Welt"));
                nodes2Messages.awaitCount(4).assertValueCount(4)
                        .assertValueAt(0, m -> m.getPayload().equals("Hallo Welt"))
                        .assertValueAt(1, m -> m.getPayload().equals("Hallo Welt"))
                        .assertValueAt(2, m -> m.getPayload().equals("Hallo Welt"))
                        .assertValueAt(3, m -> m.getPayload().equals("Hallo Welt"));
                node3Messages.awaitCount(4).assertValueCount(4)
                        .assertValueAt(0, m -> m.getPayload().equals("Hallo Welt"))
                        .assertValueAt(1, m -> m.getPayload().equals("Hallo Welt"))
                        .assertValueAt(2, m -> m.getPayload().equals("Hallo Welt"));
                node4Messages.awaitCount(4).assertValueCount(4)
                        .assertValueAt(0, m -> m.getPayload().equals("Hallo Welt"))
                        .assertValueAt(1, m -> m.getPayload().equals("Hallo Welt"))
                        .assertValueAt(2, m -> m.getPayload().equals("Hallo Welt"))
                        .assertValueAt(3, m -> m.getPayload().equals("Hallo Welt"));
            }

            /**
             * This test checks whether the {@link org.drasyl.intravm.IntraVmDiscovery} emits the
             * correct {@link PeerEvent}s.
             */
            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void correctPeerEventsShouldBeEmitted() {
                final TestObserver<PeerDirectEvent> node1Events = node1.events(PeerDirectEvent.class).test();
                final TestObserver<PeerDirectEvent> node2Events = node2.events(PeerDirectEvent.class).test();
                final TestObserver<PeerDirectEvent> node3Events = node3.events(PeerDirectEvent.class).test();
                final TestObserver<PeerDirectEvent> node4Events = node4.events(PeerDirectEvent.class).test();

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
         * +--------+   +--------+
         * | Node 1 |   | Node 2 |
         * +--------+   +--------+
         * </pre>
         */
        @Nested
        class FourNodesWithOnlyLocalHostDiscoveryEnabled {
            private EmbeddedNode node1;
            private EmbeddedNode node2;

            @BeforeEach
            void setUp(@TempDir final Path localHostDiscoveryPath) throws DrasylException {
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
                        .remoteLocalHostDiscoveryEnabled(true)
                        .remoteLocalHostDiscoveryLeaseTime(ofSeconds(1))
                        .remoteLocalHostDiscoveryPath(localHostDiscoveryPath)
                        .build();
                node1 = new EmbeddedNode(config).started();
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
                        .remoteLocalHostDiscoveryEnabled(true)
                        .remoteLocalHostDiscoveryLeaseTime(ofSeconds(1))
                        .remoteLocalHostDiscoveryPath(localHostDiscoveryPath)
                        .build();
                node2 = new EmbeddedNode(config).started();
                colorizedPrintln("CREATED node2", COLOR_CYAN, STYLE_REVERSED);
            }

            @AfterEach
            void tearDown() {
                node1.close();
                node2.close();
            }

            /**
             * This test checks whether the {@link org.drasyl.localhost.LocalHostDiscovery} emits
             * the correct {@link PeerEvent}s and is able to route outgoing messages.
             */
            @Test
            @Timeout(value = TIMEOUT * 5, unit = MILLISECONDS)
            void applicationMessagesShouldBeDelivered() throws ExecutionException, InterruptedException {
                // WatchService can be ridiculous slow in reporting changes...wait up to 12*5 seconds...
                node1.events(PeerDirectEvent.class).test()
                        .awaitCount(1).awaitCount(1)
                        .awaitCount(1).awaitCount(1)
                        .awaitCount(1).awaitCount(1)
                        .awaitCount(1).awaitCount(1)
                        .awaitCount(1).awaitCount(1)
                        .awaitCount(1).awaitCount(1);
                node2.events(PeerDirectEvent.class).test()
                        .awaitCount(1).awaitCount(1)
                        .awaitCount(1).awaitCount(1)
                        .awaitCount(1).awaitCount(1)
                        .awaitCount(1).awaitCount(1)
                        .awaitCount(1).awaitCount(1)
                        .awaitCount(1).awaitCount(1);

                final TestObserver<MessageEvent> node1Messages = node1.messages().test();
                final TestObserver<MessageEvent> nodes2Messages = node2.messages().test();

                //
                // send messages
                //
                node1.send("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22", "Hallo Welt").get();
                node2.send("03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a", "Hallo Welt").get();

                //
                // verify
                //
                node1Messages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(MessageEvent.of(CompressedPublicKey.of("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"), "Hallo Welt"));
                nodes2Messages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(MessageEvent.of(CompressedPublicKey.of("03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a"), "Hallo Welt"));
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
        private EmbeddedNode node;

        @BeforeEach
        void setUp() throws DrasylException {
            //
            // create nodes
            //
            final DrasylConfig config;

            // node
            config = DrasylConfig.newBuilder()
                    .networkId(0)
                    .identityProofOfWork(ProofOfWork.of(12304070))
                    .identityPublicKey(CompressedPublicKey.of("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4"))
                    .identityPrivateKey(CompressedPrivateKey.of("073a34ecaff06fdf3fbe44ddf3abeace43e3547033493b1ac4c0ae3c6ecd6173"))
                    .remoteExposeEnabled(false)
                    .remoteEnabled(false)
                    .remoteSuperPeerEnabled(false)
                    .intraVmDiscoveryEnabled(false)
                    .remoteLocalHostDiscoveryEnabled(false)
                    .build();
            node = new EmbeddedNode(config).started();
            colorizedPrintln("CREATED node1", COLOR_CYAN, STYLE_REVERSED);
        }

        @AfterEach
        void tearDown() {
            node.close();
        }

        /**
         * This test ensures that loopback message discovery work.
         */
        @Test
        @Timeout(value = TIMEOUT, unit = MILLISECONDS)
        void applicationMessagesShouldBeDelivered() {
            final TestObserver<MessageEvent> node1Messages = node.messages().test();

            node.send("025e91733428b535e812fd94b0372c4bf2d52520b45389209acfd40310ce305ff4", "Hallo Welt");

            node1Messages.awaitCount(1).assertValueCount(1)
                    .assertValue(m -> m.getPayload().equals("Hallo Welt"));
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
            private EmbeddedNode node;

            @BeforeEach
            void setUp() throws DrasylException {
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
                        .remoteLocalHostDiscoveryEnabled(false)
                        .build();
                node = new EmbeddedNode(config);
                colorizedPrintln("CREATED node1", COLOR_CYAN, STYLE_REVERSED);
            }

            @AfterEach
            void tearDown() {
                node.close();
            }

            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void sendToSelfShouldThrowException() {
                assertThrows(ExecutionException.class, () -> node.send("025fd887836759d83b9a5e1bc565e098351fd5b86aaa184e3fb95d6598e9f9398e", "Hallo Welt").get());
            }

            @Test
            @Timeout(value = TIMEOUT, unit = MILLISECONDS)
            void sendToAnOtherPeerShouldThrowException() {
                assertThrows(ExecutionException.class, () -> node.send("030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22", "Hallo Welt").get());
            }
        }
    }
}
