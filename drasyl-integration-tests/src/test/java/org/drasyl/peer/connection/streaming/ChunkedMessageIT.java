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

import com.typesafe.config.ConfigFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.reactivex.rxjava3.core.Observable;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityManager;
import org.drasyl.identity.IdentityManagerException;
import org.drasyl.messenger.Messenger;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.RequestMessage;
import org.drasyl.peer.connection.server.NodeServer;
import org.drasyl.peer.connection.server.TestNodeServerConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import testutils.AnsiColor;

import java.util.Random;
import java.util.concurrent.ExecutionException;

import static org.drasyl.peer.connection.server.TestNodeServerConnection.clientSessionAfterJoin;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static testutils.TestHelper.colorizedPrintln;

class ChunkedMessageIT {
    private static EventLoopGroup workerGroup;
    private static EventLoopGroup bossGroup;
    DrasylNodeConfig config;
    private NodeServer server;
    private Identity identitySession1;
    private Identity identitySession2;

    @BeforeEach
    void setup(TestInfo info) throws DrasylException, CryptoException {
        colorizedPrintln("STARTING " + info.getDisplayName(), AnsiColor.COLOR_CYAN, AnsiColor.STYLE_REVERSED);

        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
        System.setProperty("io.netty.leakDetection.level", "DISABLED"); //DISABLED

        identitySession1 = Identity.of(169092, "030a59784f88c74dcd64258387f9126739c3aeb7965f36bb501ff01f5036b3d72b", "0f1e188d5e3b98daf2266d7916d2e1179ae6209faa7477a2a66d4bb61dab4399");
        identitySession2 = Identity.of(26778671, "0236fde6a49564a0eaa2a7d6c8f73b97062d5feb36160398c08a5b73f646aa5fe5", "093d1ee70518508cac18eaf90d312f768c14d43de9bfd2618a2794d8df392da0");

        config = new DrasylNodeConfig(ConfigFactory.load("configs/ChunkedMessageIT.conf"));
        DrasylNode.setLogLevel(config.getLoglevel());
        IdentityManager identityManager = new IdentityManager(config);
        identityManager.loadOrCreateIdentity();
        PeersManager peersManager = new PeersManager(event -> {
        });
        Messenger messenger = new Messenger();
        Observable<Boolean> superPeerConnected = Observable.just(false);

        server = new NodeServer(identityManager, messenger, peersManager, superPeerConnected, config, workerGroup, bossGroup);
        server.open();
    }

    @AfterEach
    void cleanUp(TestInfo info) throws IdentityManagerException {
        server.close();

        IdentityManager.deleteIdentityFile(config.getIdentityPath());

        colorizedPrintln("FINISHED " + info.getDisplayName(), AnsiColor.COLOR_CYAN, AnsiColor.STYLE_REVERSED);
    }

    @Test
    void messageWithMaxSizeShouldArrive() throws InterruptedException, ExecutionException {
        // create connection
        TestNodeServerConnection session1 = clientSessionAfterJoin(server, identitySession1);
        TestNodeServerConnection session2 = clientSessionAfterJoin(server, identitySession2);

        Observable<Message> receivedMessages = session2.receivedMessages().filter(msg -> msg instanceof ApplicationMessage);

        // create message with max allowed payload size
        byte[] bigPayload = new byte[config.getMessageMaxContentLength()];
        new Random().nextBytes(bigPayload);

        // send message
        RequestMessage request = new ApplicationMessage(session1.getPublicKey(), session2.getPublicKey(), bigPayload);
        session2.send(request);

        // verify response
        assertEquals(request, receivedMessages.blockingFirst());
    }

    @BeforeAll
    static void beforeAll() {
        workerGroup = new NioEventLoopGroup(16);
        bossGroup = new NioEventLoopGroup(1);
    }

    @AfterAll
    static void afterAll() {
        workerGroup.shutdownGracefully().syncUninterruptibly();
        bossGroup.shutdownGracefully().syncUninterruptibly();
    }
}
