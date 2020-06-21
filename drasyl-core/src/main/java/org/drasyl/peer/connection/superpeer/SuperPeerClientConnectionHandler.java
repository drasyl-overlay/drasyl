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
package org.drasyl.peer.connection.superpeer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.DrasylNodeConfig;
import org.drasyl.event.Event;
import org.drasyl.event.Node;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.messenger.MessageSink;
import org.drasyl.messenger.Messenger;
import org.drasyl.messenger.NoPathToIdentityException;
import org.drasyl.peer.Path;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.connection.handler.AbstractThreeWayHandshakeClientHandler;
import org.drasyl.peer.connection.message.ConnectionExceptionMessage;
import org.drasyl.peer.connection.message.JoinMessage;
import org.drasyl.peer.connection.message.StatusMessage;
import org.drasyl.peer.connection.message.WelcomeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.drasyl.event.EventType.EVENT_NODE_OFFLINE;
import static org.drasyl.event.EventType.EVENT_NODE_ONLINE;
import static org.drasyl.peer.connection.message.ConnectionExceptionMessage.Error.CONNECTION_ERROR_WRONG_PUBLIC_KEY;
import static org.drasyl.peer.connection.server.NodeServerChannelGroup.ATTRIBUTE_PUBLIC_KEY;

/**
 * This handler performs the handshake with the server and processes incoming messages during the
 * session.
 * <p>
 * The handshake is initiated by a {@link JoinMessage} sent by the client, which is answered with a
 * {@link WelcomeMessage} from the server. The client must then confirm this message with a {@link
 * StatusMessage}.
 */
@SuppressWarnings({ "java:S110" })
public class SuperPeerClientConnectionHandler extends AbstractThreeWayHandshakeClientHandler<JoinMessage, WelcomeMessage> {
    public static final String SUPER_PEER_CLIENT_CONNECTION_HANDLER = "superPeerClientConnectionHandler";
    private static final Logger LOG = LoggerFactory.getLogger(SuperPeerClientConnectionHandler.class);
    private final DrasylNodeConfig config;
    private final SuperPeerClient client;

    public SuperPeerClientConnectionHandler(DrasylNodeConfig config,
                                            SuperPeerClient client) {
        super(
                config.getSuperPeerHandshakeTimeout(),
                client.getMessenger(),
                new JoinMessage(client.getIdentity().getProofOfWork(),
                        client.getIdentity().getPublicKey(),
                        client.getPeersManager().getChildrenAndGrandchildren().keySet()
                )
        );
        this.config = config;
        this.client = client;
    }

    @SuppressWarnings({ "java:S107" })
    SuperPeerClientConnectionHandler(DrasylNodeConfig config,
                                     SuperPeerClient client,
                                     Duration timeout,
                                     Messenger messenger,
                                     CompletableFuture<Void> handshakeFuture,
                                     ScheduledFuture<?> timeoutFuture,
                                     JoinMessage requestMessage) {
        super(timeout, messenger, handshakeFuture, timeoutFuture, requestMessage);
        this.config = config;
        this.client = client;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        ctx.channel().closeFuture().addListener(future -> messenger.unsetRelaySink());
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }

    @Override
    protected ConnectionExceptionMessage.Error validateSessionOffer(WelcomeMessage offerMessage) {
        CompressedPublicKey superPeerPublicKey = offerMessage.getPublicKey();
        if (config.getSuperPeerPublicKey() != null && !superPeerPublicKey.equals(config.getSuperPeerPublicKey())) {
            return CONNECTION_ERROR_WRONG_PUBLIC_KEY;
        }
        else if (superPeerPublicKey.equals(client.getIdentity().getPublicKey())) {
            return CONNECTION_ERROR_WRONG_PUBLIC_KEY;
        }
        else {
            return null;
        }
    }

    @Override
    protected void createConnection(ChannelHandlerContext ctx,
                                    WelcomeMessage offerMessage) {
        CompressedPublicKey identity = offerMessage.getPublicKey();
        Channel channel = ctx.channel();
        Path path = ctx::writeAndFlush; // We start at this point to save resources
        PeerInformation peerInformation = PeerInformation.of(offerMessage.getPeerInformation().getEndpoints(), path);

        // attach identity to channel (this information is required for validation signatures of incoming messages)
        channel.attr(ATTRIBUTE_PUBLIC_KEY).set(identity);

        // remove peer information on disconnect
        channel.closeFuture().addListener(future -> {
            client.getConnected().onNext(false);
            client.getEventConsumer().accept(new Event(EVENT_NODE_OFFLINE, Node.of(client.getIdentity())));

            client.getPeersManager().unsetSuperPeerAndRemovePeerInformation(peerInformation);
        });

        // store peer information
        client.getPeersManager().addPeerInformationAndSetSuperPeer(identity, peerInformation);

        MessageSink messageSink = (recipient, message) -> {
            if (channel.isWritable()) {
                ctx.writeAndFlush(message);
            }
            else {
                throw new NoPathToIdentityException(recipient);
            }
        };
        messenger.setSuperPeerSink(messageSink);

        client.getConnected().onNext(true);
        client.getEventConsumer().accept(new Event(EVENT_NODE_ONLINE, Node.of(client.getIdentity())));
    }
}