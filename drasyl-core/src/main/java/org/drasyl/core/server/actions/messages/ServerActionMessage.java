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
package org.drasyl.core.server.actions.messages;

import org.drasyl.core.common.messages.Message;
import org.drasyl.core.common.messages.Response;
import org.drasyl.core.common.messages.Status;
import org.drasyl.core.models.DrasylException;
import org.drasyl.core.node.identity.Identity;
import org.drasyl.core.server.NodeServer;
import org.drasyl.core.server.actions.ServerAction;
import org.drasyl.core.server.session.ServerSession;

import java.util.Objects;

public class ServerActionMessage extends Message implements ServerAction {
    ServerActionMessage() {
        super();
    }

    ServerActionMessage(Identity sender, Identity recipient, byte[] payload) {
        super(sender, recipient, payload);
    }

    @Override
    public void onMessage(ServerSession session, NodeServer nodeServer) {
        Objects.requireNonNull(session);
        Objects.requireNonNull(nodeServer);

        try {
            nodeServer.send(copyOf(this));
            session.send(new Response<>(Status.OK, this.getMessageID()));
        }
        catch (DrasylException exception) {
            session.send(new Response<>(Status.NOT_FOUND, this.getMessageID()));
        }
    }

    @Override
    public void onResponse(String responseMsgID, ServerSession session, NodeServer nodeServer) {
        // This message does not comes as response to the relay server
    }
}