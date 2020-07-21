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
package org.drasyl.peer.connection.client;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.crypto.CryptoException;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.Node;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.IdentityManager;
import org.drasyl.identity.IdentityManagerException;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.PeerChannelGroup;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.PingMessage;
import org.drasyl.peer.connection.message.PongMessage;
import org.drasyl.peer.connection.message.QuitMessage;
import org.drasyl.peer.connection.message.RequestMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.drasyl.peer.connection.server.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.peer.connection.handler.stream.ChunkedMessageHandler.CHUNK_SIZE;
import static org.drasyl.peer.connection.message.QuitMessage.CloseReason.REASON_SHUTTING_DOWN;
import static org.drasyl.peer.connection.message.StatusMessage.Code.STATUS_OK;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static testutils.AnsiColor.COLOR_CYAN;
import static testutils.AnsiColor.STYLE_REVERSED;
import static testutils.TestHelper.colorizedPrintln;

@Execution(ExecutionMode.SAME_THREAD)
class SuperPeerClientIT {
    public static final long TIMEOUT = 10000L;
    DrasylConfig config;
    DrasylConfig serverConfig;
    private EventLoopGroup workerGroup;
    private EventLoopGroup serverWorkerGroup;
    private EventLoopGroup bossGroup;
    private IdentityManager identityManager;
    private IdentityManager identityManagerServer;
    private TestServer server;
    private Messenger messenger;
    private Messenger messengerServer;
    private PeersManager peersManager;
    private PeersManager peersManagerServer;
    private Subject<Event> emittedEventsSubject;
    private Observable<Boolean> superPeerConnected;
    private PeerChannelGroup channelGroup;
    private PeerChannelGroup channelGroupServer;
    private Set<URI> endpoints;

    @BeforeEach
    void setup(TestInfo info) throws DrasylException, CryptoException {
        colorizedPrintln("STARTING " + info.getDisplayName(), COLOR_CYAN, STYLE_REVERSED);

        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
        System.setProperty("io.netty.leakDetection.level", "PARANOID");

        workerGroup = new NioEventLoopGroup();
        serverWorkerGroup = new NioEventLoopGroup();
        bossGroup = new NioEventLoopGroup(1);

        config = DrasylConfig.newBuilder()
//                .loglevel(Level.TRACE)
                .identityProofOfWork(ProofOfWork.of(6657650))
                .identityPublicKey(CompressedPublicKey.of("023d34f317616c3bb0fa1e4b425e9419d1704ef57f6e53afe9790e00998134f5ff"))
                .identityPrivateKey(CompressedPrivateKey.of("0c27af38c77f2cd5cc2a0ff5c461003a9c24beb955f316135d251ecaf4dda03f"))
                .serverBindHost("127.0.0.1")
                .serverBindPort(0)
                .serverHandshakeTimeout(ofSeconds(5))
                .serverSSLEnabled(true)
                .serverIdleTimeout(ofSeconds(1))
                .serverIdleRetries((short) 1)
                .superPeerEndpoints(Set.of(URI.create("wss://127.0.0.1:22527")))
                .superPeerRetryDelays(List.of(ofSeconds(0), ofSeconds(1), ofSeconds(2), ofSeconds(4), ofSeconds(8), ofSeconds(16), ofSeconds(32), ofSeconds(60)))
                .superPeerIdleTimeout(ofSeconds(1))
                .superPeerIdleRetries((short) 1)
                .messageMaxContentLength(CHUNK_SIZE + 1)
                .superPeerPublicKey(CompressedPublicKey.of("0234789936c7941f850c382ea9d14ecb0aad03b99a9e29a9c15b42f5f1b0c4cf3d"))
                .build();
        DrasylNode.setLogLevel(config.getLoglevel());
        identityManager = new IdentityManager(config);
        identityManager.loadOrCreateIdentity();

        serverConfig = DrasylConfig.newBuilder()
                .identityProofOfWork(ProofOfWork.of(5344366))
                .identityPublicKey(CompressedPublicKey.of("0234789936c7941f850c382ea9d14ecb0aad03b99a9e29a9c15b42f5f1b0c4cf3d"))
                .identityPrivateKey(CompressedPrivateKey.of("064f10d37111303ee20443661c8ea758045bbf809e4950dd84b8a1348863d0f8"))
                .serverBindHost("127.0.0.1")
                .serverHandshakeTimeout(ofSeconds(5))
                .serverSSLEnabled(true)
                .serverIdleTimeout(ofSeconds(1))
                .serverIdleRetries((short) 1)
                .superPeerEnabled(false)
                .messageMaxContentLength(CHUNK_SIZE + 2)
                .build();
        identityManagerServer = new IdentityManager(serverConfig);
        identityManagerServer.loadOrCreateIdentity();
        peersManager = new PeersManager(event -> {
        });
        channelGroup = new PeerChannelGroup();
        peersManagerServer = new PeersManager(event -> {
        });
        channelGroupServer = new PeerChannelGroup();
        messenger = new Messenger(message -> {
        }, peersManager, channelGroup);
        messengerServer = new Messenger(message -> {
        }, peersManager, channelGroup);
        endpoints = new HashSet<>();

        server = new TestServer(identityManagerServer::getIdentity, messengerServer, peersManagerServer, serverConfig, channelGroupServer, serverWorkerGroup, bossGroup, superPeerConnected, endpoints);
        server.open();
        emittedEventsSubject = ReplaySubject.<Event>create().toSerialized();
        superPeerConnected = BehaviorSubject.createDefault(false).toSerialized();
    }

    @AfterEach
    void cleanUp(TestInfo info) throws IdentityManagerException {
        server.close();

        IdentityManager.deleteIdentityFile(config.getIdentityPath());
        workerGroup.shutdownGracefully().syncUninterruptibly();
        bossGroup.shutdownGracefully().syncUninterruptibly();
        serverWorkerGroup.shutdownGracefully().syncUninterruptibly();
        colorizedPrintln("FINISHED " + info.getDisplayName(), COLOR_CYAN, STYLE_REVERSED);
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldSendJoinMessageOnConnect() throws ClientException {
        TestObserver<Message> receivedMessages = server.receivedMessages().test();

        // start client
        try (SuperPeerClient client = new SuperPeerClient(config, identityManager.getIdentity(), peersManager, messenger, channelGroup, workerGroup, event -> {
        }, publicKey -> {
        }, () -> true)) {
            client.open();

            // verify received messages
            receivedMessages.awaitCount(1);
            receivedMessages.assertValueAt(0, new JoinMessage(identityManager.getProofOfWork(), identityManager.getPublicKey(), Set.of()));
        }
    }

    @Disabled("Race Condition error")
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldSendQuitMessageOnClientSideDisconnect() throws ClientException {
        TestObserver<Message> receivedMessages = server.receivedMessages().filter(m -> m instanceof QuitMessage).test();
        TestObserver<Event> emittedEvents = emittedEventsSubject.test();

        // start client
        SuperPeerClient client = new SuperPeerClient(config, identityManager.getIdentity(), peersManager, messenger, channelGroup, workerGroup, emittedEventsSubject::onNext, publicKey -> {
        }, () -> true);
        client.open();

        // wait for node to become online, before closing it
        emittedEvents.awaitCount(1);
        client.close();

        // verify emitted events
        receivedMessages.awaitCount(1);
        receivedMessages.assertValueAt(0, new QuitMessage(REASON_SHUTTING_DOWN));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldEmitNodeOfflineEventOnClientSideDisconnect() throws ClientException {
        TestObserver<Event> emittedEvents = emittedEventsSubject.test();

        // start client
        SuperPeerClient client = new SuperPeerClient(config, identityManager.getIdentity(), peersManager, messenger, channelGroup, workerGroup, emittedEventsSubject::onNext, publicKey -> {
        }, () -> true);
        client.open();

        // wait for node to become online, before closing it
        emittedEvents.awaitCount(1);
        client.close();

        // verify emitted events
        emittedEvents.awaitCount(2);
        emittedEvents.assertValueAt(1, new NodeOfflineEvent(Node.of(identityManager.getIdentity())));
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldRespondToPingMessageWithPongMessage() throws ClientException {
        PingMessage request = new PingMessage();
        TestObserver<Message> receivedMessages = server.receivedMessages().filter(m -> m instanceof PongMessage && ((PongMessage) m).getCorrespondingId().equals(request.getId())).test();

        // start client
        try (SuperPeerClient client = new SuperPeerClient(config, identityManager.getIdentity(), peersManager, messenger, channelGroup, workerGroup, event -> {
        }, publicKey -> {
        }, () -> true)) {
            client.open();
            server.awaitClient(identityManager.getPublicKey());

            // send message
            server.sendMessage(identityManager.getPublicKey(), request);

            // verify received message
            receivedMessages.awaitCount(1);
        }
    }

    @Test
    @Disabled("disabled, because StatusMessage is currently not used and therefore has been removed.")
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldRespondToApplicationMessageWithStatusOk() throws ClientException {
        TestObserver<Message> receivedMessages = server.receivedMessages().filter(m -> m instanceof StatusMessage).test();

        // start client
        try (SuperPeerClient client = new SuperPeerClient(config, identityManager.getIdentity(), peersManager, messenger, channelGroup, workerGroup, event -> {
        }, publicKey -> {
        }, () -> true)) {
            client.open();
            server.awaitClient(identityManager.getPublicKey());

            // send message
            ApplicationMessage request = new ApplicationMessage(identityManagerServer.getPublicKey(), identityManager.getPublicKey(), new byte[]{
                    0x00,
                    0x01
            }, byte[].class);
            server.sendMessage(identityManager.getPublicKey(), request);

            // verify received message
            receivedMessages.awaitCount(2);
            receivedMessages.assertValueAt(1, new StatusMessage(STATUS_OK, request.getId()));
        }
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldEmitNodeOfflineEventAfterReceivingQuitMessage() throws ClientException {
        TestObserver<Event> emittedEvents = emittedEventsSubject.test();

        // start client
        DrasylConfig noRetryConfig = DrasylConfig.newBuilder(config).superPeerRetryDelays(List.of()).build();
        try (SuperPeerClient client = new SuperPeerClient(noRetryConfig, identityManager.getIdentity(), peersManager, messenger, channelGroup, workerGroup, emittedEventsSubject::onNext, publicKey -> {
        }, () -> true)) {
            client.open();
            server.awaitClient(identityManager.getPublicKey());

            // send message
            server.sendMessage(identityManager.getPublicKey(), new QuitMessage());

            // verify emitted events
            emittedEvents.awaitCount(2);
            emittedEvents.assertValueAt(1, new NodeOfflineEvent(Node.of(identityManager.getIdentity())));
        }
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldEmitNodeOnlineEventAfterReceivingWelcomeMessage() throws ClientException {
        TestObserver<Event> emittedEvents = emittedEventsSubject.test();

        // start client
        try (SuperPeerClient client = new SuperPeerClient(config, identityManager.getIdentity(), peersManager, messenger, channelGroup, workerGroup, emittedEventsSubject::onNext, publicKey -> {
        }, () -> true)) {
            client.open();

            // verify emitted events
            emittedEvents.awaitCount(1);
            emittedEvents.assertValue(new NodeOnlineEvent(Node.of(identityManager.getIdentity())));
        }
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldEmitNodeOnlineAlsoWithoutSuperPeerPubKeyInConfig() throws ClientException {
        TestObserver<Event> emittedEvents = emittedEventsSubject.test();

        DrasylConfig config1 = DrasylConfig.newBuilder(config)
                .superPeerPublicKey(null)
                .build();

        // start client
        try (SuperPeerClient client = new SuperPeerClient(config1, identityManager.getIdentity(), peersManager, messenger, channelGroup, workerGroup, emittedEventsSubject::onNext, publicKey -> {
        }, () -> true)) {
            client.open();

            // verify emitted events
            emittedEvents.awaitCount(1);
            emittedEvents.assertValue(new NodeOnlineEvent(Node.of(identityManager.getIdentity())));
        }
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void clientShouldReconnectOnDisconnect() throws ClientException {
        TestObserver<Event> emittedEvents = emittedEventsSubject.test();

        DrasylConfig config1 = DrasylConfig.newBuilder(config)
                .superPeerPublicKey(null)
                .build();

        // start client
        try (SuperPeerClient client = new SuperPeerClient(config, identityManager.getIdentity(), peersManager, messenger, channelGroup, workerGroup, emittedEventsSubject::onNext, publicKey -> {
        }, () -> true)) {
            client.open();
            server.awaitClient(identityManager.getPublicKey());

            // server-side disconnect
            server.closeClient(identityManager.getPublicKey());

            // verify emitted events
            emittedEvents.awaitCount(3); // wait for EVENT_NODE_OFFLINE and EVENT_NODE_ONLINE
            emittedEvents.assertValueAt(0, new NodeOnlineEvent(Node.of(identityManager.getIdentity(), Set.of())));
            emittedEvents.assertValueAt(1, new NodeOfflineEvent(Node.of(identityManager.getIdentity(), Set.of())));
            emittedEvents.assertValueAt(2, new NodeOnlineEvent(Node.of(identityManager.getIdentity(), Set.of())));
        }
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void messageExceedingMaxSizeShouldNotBeSend() throws ClientException {
        TestObserver<Message> receivedMessages = server.receivedMessages().filter(m -> m instanceof StatusMessage).test();
        TestObserver<Event> emittedEvents = emittedEventsSubject.filter(e -> e instanceof MessageEvent).test();

        // start client
        try (SuperPeerClient client = new SuperPeerClient(config, identityManager.getIdentity(), peersManager, messenger, channelGroup, workerGroup, emittedEventsSubject::onNext, publicKey -> {
        }, () -> true)) {
            client.open();
            server.awaitClient(identityManager.getPublicKey());

            // create message with exceeded payload size of the client, but not of the server
            byte[] bigPayload = new byte[serverConfig.getMessageMaxContentLength()];
            new Random().nextBytes(bigPayload);

            // send message
            RequestMessage request = new ApplicationMessage(identityManagerServer.getPublicKey(), identityManager.getPublicKey(), bigPayload, bigPayload.getClass());
            server.sendMessage(identityManager.getPublicKey(), request);

            receivedMessages.awaitCount(2);
            emittedEvents.assertNoValues();
            receivedMessages.assertValueAt(1, new StatusMessage(StatusMessage.Code.STATUS_PAYLOAD_TOO_LARGE, request.getId()));
        }
    }

    @Test
    @Timeout(value = TIMEOUT, unit = MILLISECONDS)
    void messageExceedingMaxSizeShouldOnSendShouldThrowException() throws ClientException {
        // start client
        try (SuperPeerClient client = new SuperPeerClient(config, identityManager.getIdentity(), peersManager, messenger, channelGroup, workerGroup, emittedEventsSubject::onNext, publicKey -> {
        }, () -> true)) {
            client.open();
            server.awaitClient(identityManager.getPublicKey());

            // create message with exceeded payload size of client and server
            byte[] bigPayload = new byte[serverConfig.getMessageMaxContentLength() + 1];
            new Random().nextBytes(bigPayload);

            // send message
            RequestMessage request = new ApplicationMessage(identityManagerServer.getPublicKey(), identityManager.getPublicKey(), bigPayload, bigPayload.getClass());
            assertThrows(RuntimeException.class, () -> server.sendMessage(identityManager.getPublicKey(), request));
        }
    }
}
