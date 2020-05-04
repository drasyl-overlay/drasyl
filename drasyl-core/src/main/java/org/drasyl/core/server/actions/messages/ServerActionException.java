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

import org.drasyl.core.common.messages.NodeServerException;
import org.drasyl.core.common.messages.Response;
import org.drasyl.core.common.messages.Status;
import org.drasyl.core.server.NodeServer;
import org.drasyl.core.server.actions.ServerAction;
import org.drasyl.core.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({ "squid:S2166" })
public class ServerActionException extends NodeServerException implements ServerAction {
    private static final Logger LOG = LoggerFactory.getLogger(ServerActionException.class);

    @Override
    public void onMessage(ServerSession session, NodeServer nodeServer) {
        LOG.error("Received exception message: {}", this.getException());
        session.send(new Response<>(Status.OK, this.getMessageID()));
    }

    @Override
    public void onResponse(String responseMsgID, ServerSession session, NodeServer nodeServer) {
        session.setResponse(new Response<>(this, responseMsgID));
        LOG.error("Received response with exception message: {}", this.getException());
    }
}