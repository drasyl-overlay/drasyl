/*
 * Copyright (c) 2021.
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
package org.drasyl.remote.handler;

import com.google.protobuf.MessageLite;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.Stateless;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.drasyl.remote.protocol.AddressedIntermediateEnvelope;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Acts as a guard for in- and outbound messages. <br> Signs automatically outbound messages. <br>
 * Validates automatically inbound messages and drops them, iff a signature is invalid or if a
 * validation is impossible, e.g. the public key of the sender is unknown. In this case, drop
 * information is written to the log.
 */
@Stateless
@SuppressWarnings({ "java:S110" })
public class SignatureHandler extends SimpleDuplexHandler<AddressedIntermediateEnvelope<MessageLite>, AddressedIntermediateEnvelope<MessageLite>, Address> {
    public static final SignatureHandler INSTANCE = new SignatureHandler();
    public static final String SIGNATURE_HANDLER = "SIGNATURE_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(SignatureHandler.class);

    private SignatureHandler() {
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final AddressedIntermediateEnvelope<MessageLite> msg,
                               final CompletableFuture<Void> future) {
        try {
            if (ctx.identity().getPublicKey().equals(msg.getContent().getRecipient())) {
                // disarm all messages addressed to us
                final AddressedIntermediateEnvelope<MessageLite> disarmedMessage = new AddressedIntermediateEnvelope<>(msg.getSender(), msg.getRecipient(), msg.getContent().disarmAndRelease(ctx.identity().getPrivateKey()));
                ctx.fireRead(sender, disarmedMessage, future);
            }
            else {
                ctx.fireRead(sender, msg, future);
            }
        }
        catch (final IllegalStateException e) {
            LOG.debug("Can't disarm message `{}` due to the following error: ", msg, e);
            future.completeExceptionally(new Exception("Unable to disarm message", e));
        }
    }

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final Address recipient,
                                final AddressedIntermediateEnvelope<MessageLite> msg,
                                final CompletableFuture<Void> future) {
        try {
            if (ctx.identity().getPublicKey().equals(msg.getContent().getSender())) {
                // arm all messages from us
                final AddressedIntermediateEnvelope<MessageLite> armedMessage = new AddressedIntermediateEnvelope<>(msg.getSender(), msg.getRecipient(), msg.getContent().armAndRelease(ctx.identity().getPrivateKey()));
                ctx.write(recipient, armedMessage, future);
            }
            else {
                ctx.write(recipient, msg, future);
            }
        }
        catch (final IllegalStateException e) {
            LOG.debug("Can't arm message `{}` due to the following error: ", msg, e);
            future.completeExceptionally(new Exception("Unable to arm message", e));
        }
    }
}
