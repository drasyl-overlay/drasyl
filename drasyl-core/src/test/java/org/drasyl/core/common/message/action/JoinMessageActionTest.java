package org.drasyl.core.common.message.action;

import org.drasyl.core.common.message.JoinMessage;
import org.drasyl.core.common.message.WelcomeMessage;
import org.drasyl.core.models.CompressedKeyPair;
import org.drasyl.core.models.CompressedPublicKey;
import org.drasyl.core.node.*;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.node.identity.IdentityManager;
import org.drasyl.core.node.identity.IdentityTestHelper;
import org.drasyl.core.server.NodeServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.net.URI;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JoinMessageActionTest {
    private JoinMessage message;
    private NodeServer nodeServer;
    private ClientConnection session;
    private PeersManager peersManager;
    private CompressedPublicKey compressedPublicKey;
    private String id;
    private IdentityManager identityManager;
    private CompressedKeyPair keyPair;
    private Messenger messenger;
    private ConnectionsManager connectionsManager;
    private String correspondingId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        message = mock(JoinMessage.class);
        nodeServer = mock(NodeServer.class);
        session = mock(ClientConnection.class);
        peersManager = mock(PeersManager.class);
        compressedPublicKey = mock(CompressedPublicKey.class);
        id = "id";
        correspondingId = "correspondingId";

        identityManager = mock(IdentityManager.class);
        keyPair = mock(CompressedKeyPair.class);
        messenger = mock(Messenger.class);
        connectionsManager = mock(ConnectionsManager.class);

        when(compressedPublicKey.toString()).thenReturn(IdentityTestHelper.random().getId());
        when(nodeServer.getPeersManager()).thenReturn(peersManager);
        when(nodeServer.getMyIdentity()).thenReturn(identityManager);
        when(identityManager.getKeyPair()).thenReturn(keyPair);
        when(keyPair.getPublicKey()).thenReturn(compressedPublicKey);
        when(nodeServer.getEntryPoints()).thenReturn(Set.of(URI.create("ws://testURI")));
        when(nodeServer.getMessenger()).thenReturn(messenger);
        when(messenger.getConnectionsManager()).thenReturn(connectionsManager);
    }

    @Test
    public void onMessageServerShouldAddPeerToPeersManager() {
        when(message.getPublicKey()).thenReturn(compressedPublicKey);
        when(message.getEndpoints()).thenReturn(Set.of());
        when(message.getId()).thenReturn(id);

        JoinMessageAction action = new JoinMessageAction(message);
        action.onMessageServer(session, nodeServer);

        PeerInformation myPeerInformation = new PeerInformation();
        myPeerInformation.setPublicKey(message.getPublicKey());
        myPeerInformation.addEndpoint(message.getEndpoints());

        verify(peersManager).addPeer(Identity.of(compressedPublicKey), myPeerInformation);
        verify(peersManager).addChildren(Identity.of(compressedPublicKey));

        verify(session).send(new WelcomeMessage(nodeServer.getMyIdentity().getKeyPair().getPublicKey(), nodeServer.getEntryPoints(), id));
    }
}