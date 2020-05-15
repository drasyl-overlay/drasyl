package org.drasyl.core.common.message.action;

import com.typesafe.config.ConfigFactory;
import org.drasyl.core.common.message.ClientsStocktakingMessage;
import org.drasyl.core.common.message.RequestClientsStocktakingMessage;
import org.drasyl.core.node.DrasylNodeConfig;
import org.drasyl.core.node.PeersManager;
import org.drasyl.core.node.connections.ClientConnection;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.server.NodeServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;

import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.*;

class RequestClientsStocktakingMessageActionTest {
    private ClientConnection clientConnection;
    private NodeServer server;
    private PeersManager peersManager;
    @Captor
    private ArgumentCaptor<ClientsStocktakingMessage> captor;
    private RequestClientsStocktakingMessage message;
    private String id;
    private Identity identity1;
    private Identity identity2;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);

        message = mock(RequestClientsStocktakingMessage.class);
        clientConnection = mock(ClientConnection.class);
        server = mock(NodeServer.class);
        peersManager = mock(PeersManager.class);
        id = "id";
        identity1 = mock(Identity.class);
        identity2 = mock(Identity.class);

        when(server.getPeersManager()).thenReturn(peersManager);
        when(server.getConfig()).thenReturn(new DrasylNodeConfig(ConfigFactory.load()));
        when(message.getId()).thenReturn(id);
    }

    @Test
    void onMessageServerShouldSendListWithAllChildren() {
        when(peersManager.getChildren()).thenReturn(Set.of(identity1, identity2));

        RequestClientsStocktakingMessageAction action = new RequestClientsStocktakingMessageAction(message);
        action.onMessageServer(clientConnection, server);

        verify(clientConnection).send(captor.capture());

        ClientsStocktakingMessage asm = captor.getValue();
        assertThat(asm.getIdentities(), containsInAnyOrder(identity1, identity2));
    }
}