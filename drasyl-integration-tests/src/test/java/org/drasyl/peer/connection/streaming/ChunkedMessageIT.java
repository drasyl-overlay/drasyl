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
package org.drasyl.peer.connection.streaming;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.reactivex.rxjava3.core.Observable;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylNode;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityManager;
import org.drasyl.identity.IdentityManagerException;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.client.TestSuperPeerClient;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.RequestMessage;
import org.drasyl.peer.connection.server.Server;
import org.drasyl.peer.connection.server.ServerException;
import org.drasyl.peer.connection.server.TestServer;
import org.drasyl.peer.connection.client.ClientException;
import org.drasyl.peer.connection.superpeer.TestClientChannelInitializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.TestInfo;
import testutils.AnsiColor;

import java.net.URI;
import java.util.Random;
import java.util.Set;

import static java.time.Duration.ofSeconds;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static testutils.AnsiColor.COLOR_CYAN;
import static testutils.AnsiColor.STYLE_REVERSED;
import static testutils.TestHelper.colorizedPrintln;

@Disabled("Only for benchmark purposes")
class ChunkedMessageIT {
    private static EventLoopGroup workerGroup;
    private static EventLoopGroup bossGroup;
    private static DrasylConfig config;
    private static TestServer server;
    private static TestSuperPeerClient session1;
    private static TestSuperPeerClient session2;
    private static byte[] bigPayload;

    @BeforeEach
    void setup(TestInfo info) {
        colorizedPrintln("STARTING " + info.getDisplayName(), COLOR_CYAN, STYLE_REVERSED);
    }

    @AfterEach
    void cleanUp(TestInfo info) {
        colorizedPrintln("FINISHED " + info.getDisplayName(), AnsiColor.COLOR_CYAN, AnsiColor.STYLE_REVERSED);
    }

    @RepeatedTest(5)
    void messageWithMaxSizeShouldArrive() {
        Observable<Message> receivedMessages = session2.receivedMessages().filter(msg -> msg instanceof ApplicationMessage);

        // send message
        RequestMessage request = new ApplicationMessage(session1.getPublicKey(), session2.getPublicKey(), bigPayload);
        session2.send(request);

        // verify response
        assertEquals(request, receivedMessages.blockingFirst());
    }

    @BeforeAll
    static void beforeAll() throws IdentityManagerException, ServerException, CryptoException, ClientException {
        workerGroup = new NioEventLoopGroup();
        bossGroup = new NioEventLoopGroup(1);

        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
        System.setProperty("io.netty.leakDetection.level", "DISABLED");

        Identity identitySession1 = Identity.of(169092, "030a59784f88c74dcd64258387f9126739c3aeb7965f36bb501ff01f5036b3d72b", "0f1e188d5e3b98daf2266d7916d2e1179ae6209faa7477a2a66d4bb61dab4399");
        Identity identitySession2 = Identity.of(26778671, "0236fde6a49564a0eaa2a7d6c8f73b97062d5feb36160398c08a5b73f646aa5fe5", "093d1ee70518508cac18eaf90d312f768c14d43de9bfd2618a2794d8df392da0");

        DrasylConfig serverConfig = DrasylConfig.newBuilder()
//                .loglevel(Level.TRACE)
                .messageMaxContentLength(1024 * 1024 * 100)
                .identityProofOfWork(ProofOfWork.of(6657650))
                .identityPublicKey(CompressedPublicKey.of("023d34f317616c3bb0fa1e4b425e9419d1704ef57f6e53afe9790e00998134f5ff"))
                .identityPrivateKey(CompressedPrivateKey.of("0c27af38c77f2cd5cc2a0ff5c461003a9c24beb955f316135d251ecaf4dda03f"))
                .serverBindHost("127.0.0.1")
                .serverBindPort(0)
                .serverEndpoints(Set.of(URI.create("wss://127.0.0.1:0")))
                .serverHandshakeTimeout(ofSeconds(5))
                .serverSSLEnabled(true)
                .serverIdleTimeout(ofSeconds(1))
                .serverIdleRetries((short) 1)
                .superPeerEnabled(false)
                .build();
        DrasylNode.setLogLevel(serverConfig.getLoglevel());
        IdentityManager serverIdentityManager = new IdentityManager(serverConfig);
        serverIdentityManager.loadOrCreateIdentity();
        PeersManager peersManager = new PeersManager(event -> {
        });
        Messenger serverMessenger = new Messenger(message -> {
        });
        Observable<Boolean> serverSuperPeerConnected = Observable.just(false);

        server = new TestServer(serverIdentityManager::getIdentity, serverMessenger, peersManager, serverConfig, workerGroup, bossGroup, serverSuperPeerConnected);
        server.open();

        config = DrasylConfig.newBuilder()
//                .loglevel(Level.TRACE)
                .messageMaxContentLength(1024 * 1024 * 100)
                .serverEnabled(false)
                .serverSSLEnabled(true)
                .superPeerEndpoints(server.getEndpoints())
                .superPeerPublicKey(CompressedPublicKey.of("023d34f317616c3bb0fa1e4b425e9419d1704ef57f6e53afe9790e00998134f5ff"))
                .superPeerChannelInitializer(TestClientChannelInitializer.class)
                .build();

        session1 = clientSessionAfterJoin(config, server, identitySession1);
        session2 = clientSessionAfterJoin(config, server, identitySession2);

        // create message with max allowed payload size
        bigPayload = new byte[serverConfig.getMessageMaxContentLength()];
        new Random().nextBytes(bigPayload);
    }

    @AfterAll
    static void afterAll() throws IdentityManagerException {
        server.close();

        IdentityManager.deleteIdentityFile(config.getIdentityPath());

        workerGroup.shutdownGracefully().syncUninterruptibly();
        bossGroup.shutdownGracefully().syncUninterruptibly();
    }

    private static void awaitClientJoin(Identity identity) {
        await().until(() -> server.getChannelGroup().find(identity.getPublicKey()) != null);
    }

    private static TestSuperPeerClient clientSessionAfterJoin(DrasylConfig config,
                                                              Server server,
                                                              Identity identity) throws ClientException {
        TestSuperPeerClient client = new TestSuperPeerClient(config, server, identity, workerGroup, true, true);
        client.open();
        awaitClientJoin(identity);
        return client;
    }
}