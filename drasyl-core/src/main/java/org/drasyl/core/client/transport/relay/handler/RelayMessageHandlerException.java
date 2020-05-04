package org.drasyl.core.client.transport.relay.handler;

/**
 * A RelayMessageHandlerException is thrown by the {@link RelayMessageHandler} when errors occur.
 */
public class RelayMessageHandlerException extends Exception {
    public RelayMessageHandlerException(String message) {
        super(message);
    }
}