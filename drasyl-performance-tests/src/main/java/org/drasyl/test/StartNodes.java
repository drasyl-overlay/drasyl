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
package org.drasyl.test;

import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.annotation.NonNull;
import org.drasyl.event.Event;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.Identity;
import org.drasyl.util.RandomUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;

/**
 * This class creates a user-defined number of nodes, which do nothing else than register with the
 * super peers. Can be used as a load test for super peers. Optionally, a churn rate can be
 * specified so that nodes are continuously stopped and new ones started.
 */
@SuppressWarnings({
        "rawtypes",
        "java:S106",
        "java:S109",
        "java:S112",
        "java:S126",
        "java:S1166",
        "java:S1188",
        "java:S1941",
        "java:S2096",
        "java:S3776"
})
public class StartNodes {
    public static final int DEFAULT_NODES = 10;
    public static final String DEFAULT_IDENTITIES = "../drasyl-non-public/Identities";
    public static final int DEFAULT_CHURN = 1000;

    public static void main(final String[] args) throws IOException {
        System.out.println("INFO: Use -Dnodes=123 to specify how many nodes to start (default `" + DEFAULT_NODES + "`).");
        System.out.println("INFO: Use -Didentities=path/to/directory to specify directory to load identities from (missing identities will be generated on-the-fly) (default `" + DEFAULT_IDENTITIES + "`).");
        System.out.println("INFO: Use -Dchurn=1500 to specify how often in ms a node shold churn (default `" + DEFAULT_NODES + "`; 0 = no churn will happen).");
        final int count = SystemPropertyUtil.getInt("nodes", DEFAULT_NODES);
        final String path = SystemPropertyUtil.get("identities", DEFAULT_IDENTITIES);
        final int churn = SystemPropertyUtil.getInt("churn", DEFAULT_CHURN);
        System.out.println("nodes      = " + count);
        System.out.println("identities = " + path);
        System.out.println("churn      = " + churn);

        final IdentityProvider identityProvider = new IdentityProvider(Path.of(path));

        final List<DrasylNode> nodes = new ArrayList<>();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            final CompletableFuture[] futures = nodes.stream().map(DrasylNode::shutdown).toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(futures).join();
        }));

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                while (nodes.size() < count) {
                    try {
                        final Identity identity = identityProvider.obtain();
                        final DrasylConfig config = DrasylConfig.newBuilder()
                                .identityPrivateKey(identity.getPrivateKey())
                                .identityPublicKey(identity.getPublicKey())
                                .identityProofOfWork(identity.getProofOfWork())
                                .remoteExposeEnabled(false)
                                .intraVmDiscoveryEnabled(false)
                                .remoteLocalHostDiscoveryEnabled(false)
                                .remoteBindPort(0)
                                .build();
                        final DrasylNode node = new DrasylNode(config) {
                            @Override
                            public void onEvent(final @NonNull Event event) {
                                if (event instanceof NodeUpEvent || event instanceof NodeNormalTerminationEvent) {
                                    System.out.println(event);
                                }
                                else if (event instanceof NodeUnrecoverableErrorEvent) {
                                    System.err.println(event);
                                }
                            }
                        };
                        nodes.add(node);
                        node.start();
                    }
                    catch (final DrasylException | IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }, 0, 100);

        if (churn > 0) {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        final DrasylNode node = nodes.remove(RandomUtil.randomInt(nodes.size() - 1));
                        if (node != null) {
                            node.shutdown();
                        }
                    }
                    catch (final IndexOutOfBoundsException | IllegalArgumentException e) {
                        // ignore
                    }
                }
            }, churn, churn);
        }
    }
}
