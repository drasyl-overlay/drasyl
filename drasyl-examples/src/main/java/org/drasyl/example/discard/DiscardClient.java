/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.example.discard;

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.annotation.NonNull;
import org.drasyl.event.Event;
import org.drasyl.event.NodeOnlineEvent;

import java.nio.file.Path;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.drasyl.util.RandomUtil.randomString;

/**
 * Starts a node which keeps sending random data to given address. Based on the <a
 * href="https://tools.ietf.org/html/rfc863">Discard Protocol</a>.
 *
 * @see DiscardServer
 */
@SuppressWarnings({ "java:S106", "java:S112", "java:S2096", "StatementWithEmptyBody" })
public class DiscardClient extends DrasylNode {
    private static final String IDENTITY = System.getProperty("identity", "discard-client.identity.json");
    private static final int SIZE = Integer.parseInt(System.getProperty("size", "256"));
    private static final int PERIOD = Integer.parseInt(System.getProperty("period", "1000"));
    private final CompletableFuture<Void> online = new CompletableFuture<>();

    protected DiscardClient(final DrasylConfig config) throws DrasylException {
        super(config);
    }

    @Override
    public void onEvent(final @NonNull Event event) {
        if (event instanceof NodeOnlineEvent) {
            online.complete(null);
        }
        else {
            // ignore all other events
        }
    }

    public static void main(final String[] args) throws ExecutionException, InterruptedException, DrasylException {
        if (args.length != 1) {
            System.err.println("Please provide DiscardServer address as first argument.");
            System.exit(1);
        }
        final String recipient = args[0];

        final DrasylConfig config = DrasylConfig.newBuilder()
                .identityPath(Path.of(IDENTITY))
                .build();
        final DiscardClient node = new DiscardClient(config);

        node.start().get();
        node.online.join();
        System.out.println("DiscardClient started");

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                final String payload = randomString(SIZE);

                System.out.println("Send `" + payload + "` to `" + recipient + "`");
                node.send(recipient, payload).exceptionally(e -> {
                    throw new RuntimeException("Unable to process message.", e);
                });
            }
        }, PERIOD, PERIOD);
    }
}
