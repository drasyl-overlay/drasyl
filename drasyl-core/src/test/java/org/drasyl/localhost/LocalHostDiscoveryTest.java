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
package org.drasyl.localhost;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.DrasylConfig;
import org.drasyl.crypto.CryptoException;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.pipeline.message.ApplicationMessage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.io.FileMatchers.anExistingFile;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalHostDiscoveryTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private TypeValidator inboundValidator;
    @Mock
    private TypeValidator outboundValidator;
    @Mock
    private PeersManager peersManager;
    private final Duration leaseTime = ofSeconds(60);
    private final AtomicBoolean doScan = new AtomicBoolean();
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Path discoveryPath;
    @Mock
    private CompressedPublicKey ownPublicKey;
    @Mock
    private Set<Endpoint> endpoints;
    @Mock
    private Scheduler scheduler;
    @Mock
    private Disposable watchDisposable;
    @Mock
    private Disposable postDisposable;

    @Nested
    class StartDiscovery {
        @Test
        void shouldStartDiscoveryOnNodeUpEvent(@Mock final NodeUpEvent event) {
            when(discoveryPath.toFile().exists()).thenReturn(true);
            when(discoveryPath.toFile().isDirectory()).thenReturn(true);
            when(discoveryPath.toFile().canRead()).thenReturn(true);
            when(discoveryPath.toFile().canWrite()).thenReturn(true);

            final LocalHostDiscovery handler = new LocalHostDiscovery(discoveryPath, leaseTime, ownPublicKey, endpoints, doScan, scheduler, watchDisposable, postDisposable);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

            pipeline.processInbound(event).join();

            verify(scheduler).schedulePeriodicallyDirect(any(), eq(0L), eq(5L), eq(SECONDS));
            pipeline.close();
        }

        @Test
        void shouldTryToRegisterAWatchService(@Mock final NodeUpEvent event) throws IOException {
            when(discoveryPath.toFile().exists()).thenReturn(true);
            when(discoveryPath.toFile().isDirectory()).thenReturn(true);
            when(discoveryPath.toFile().canRead()).thenReturn(true);
            when(discoveryPath.toFile().canWrite()).thenReturn(true);

            final LocalHostDiscovery handler = new LocalHostDiscovery(discoveryPath, leaseTime, ownPublicKey, Set.of(Endpoint.of("udp://localhost:123#030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22")), doScan, scheduler, watchDisposable, postDisposable);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

            pipeline.processInbound(event).join();

            verify(discoveryPath).register(any(), eq(ENTRY_CREATE), eq(ENTRY_MODIFY), eq(ENTRY_DELETE));
            pipeline.close();
        }

        @Test
        void shouldScheduleTasksForPollingWatchServiceAndPostingOwnInformation(@Mock final NodeUpEvent event) {
            when(discoveryPath.toFile().exists()).thenReturn(true);
            when(discoveryPath.toFile().isDirectory()).thenReturn(true);
            when(discoveryPath.toFile().canRead()).thenReturn(true);
            when(discoveryPath.toFile().canWrite()).thenReturn(true);

            final LocalHostDiscovery handler = new LocalHostDiscovery(discoveryPath, leaseTime, ownPublicKey, Set.of(Endpoint.of("udp://localhost:123#030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22")), doScan, scheduler, watchDisposable, postDisposable);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

            pipeline.processInbound(event).join();

            verify(scheduler).schedulePeriodicallyDirect(any(), eq(0L), eq(5L), eq(SECONDS));
            verify(scheduler).schedulePeriodicallyDirect(any(), eq(0L), eq(ofSeconds(55L).toMillis()), eq(MILLISECONDS));
            pipeline.close();
        }

        @Test
        void scheduledTasksShouldPollWatchServiceAndPostOwnInformationToFileSystem(@TempDir final Path dir,
                                                                                   @Mock final NodeUpEvent event) throws IOException {
            final Path path = Paths.get(dir.toString(), "03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a.json");
            when(discoveryPath.toFile().exists()).thenReturn(true);
            when(discoveryPath.toFile().isDirectory()).thenReturn(true);
            when(discoveryPath.toFile().canRead()).thenReturn(true);
            when(discoveryPath.toFile().canWrite()).thenReturn(true);
            when(discoveryPath.resolve(anyString())).thenReturn(path);
            when(scheduler.schedulePeriodicallyDirect(any(), eq(0L), eq(5L), eq(SECONDS))).then(invocationOnMock -> {
                invocationOnMock.getArgument(0, Runnable.class).run();
                return null;
            });
            when(scheduler.schedulePeriodicallyDirect(any(), eq(0L), eq(ofSeconds(55L).toMillis()), eq(MILLISECONDS))).then(invocationOnMock -> {
                invocationOnMock.getArgument(0, Runnable.class).run();
                return null;
            });

            final LocalHostDiscovery handler = new LocalHostDiscovery(discoveryPath, leaseTime, ownPublicKey, Set.of(Endpoint.of("udp://localhost:123#030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22")), doScan, scheduler, watchDisposable, postDisposable);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

            pipeline.processInbound(event).join();

            verify(discoveryPath.getFileSystem().newWatchService()).poll();
            assertThat(path.toFile(), anExistingFile());
            pipeline.close();
        }
    }

    @Nested
    class StopDiscovery {
        @Test
        void shouldStopDiscoveryOnNodeUnrecoverableErrorEvent(@Mock final NodeUnrecoverableErrorEvent event,
                                                              @Mock final HandlerContext ctx) {
            final LocalHostDiscovery handler = new LocalHostDiscovery(discoveryPath, leaseTime, ownPublicKey, endpoints, doScan, scheduler, watchDisposable, postDisposable);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

            pipeline.processInbound(event).join();

            verify(watchDisposable).dispose();
            verify(postDisposable).dispose();
            pipeline.close();
        }

        @Test
        void shouldStopDiscoveryOnNodeDownEvent(@Mock final NodeDownEvent event,
                                                @Mock final HandlerContext ctx) {
            final LocalHostDiscovery handler = new LocalHostDiscovery(discoveryPath, leaseTime, ownPublicKey, endpoints, doScan, scheduler, watchDisposable, postDisposable);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

            pipeline.processInbound(event).join();

            verify(watchDisposable).dispose();
            verify(postDisposable).dispose();
            pipeline.close();
        }
    }

    @Nested
    class MessagePassing {
        @Test
        void shouldScheduleScanOnOutgoingMessage(@Mock final CompressedPublicKey recipient,
                                                 @Mock(answer = RETURNS_DEEP_STUBS) final ApplicationMessage message) {
            final LocalHostDiscovery handler = new LocalHostDiscovery(discoveryPath, leaseTime, ownPublicKey, endpoints, new AtomicBoolean(true), scheduler, watchDisposable, postDisposable);
            final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, inboundValidator, outboundValidator, handler);

            pipeline.processOutbound(recipient, message).join();

            verify(scheduler).scheduleDirect(any());
            pipeline.close();
        }
    }

    @Nested
    class Scan {
        @Test
        void shouldScanDirectory(@TempDir final Path dir,
                                 @Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx) throws IOException, CryptoException {
            when(discoveryPath.toFile()).thenReturn(dir.toFile());
            final Path path = Paths.get(dir.toString(), "03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a.json");
            Files.writeString(path, "{\"endpoints\":[\"udp://localhost:123#030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22\"]}", StandardOpenOption.CREATE);

            final LocalHostDiscovery handler = new LocalHostDiscovery(discoveryPath, leaseTime, ownPublicKey, Set.of(Endpoint.of("udp://localhost:123#030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22")), new AtomicBoolean(true), scheduler, watchDisposable, postDisposable);
            handler.scan(ctx);

            verify(ctx.peersManager()).setPeerInformation(CompressedPublicKey.of("03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a"), PeerInformation.of(Set.of(Endpoint.of("udp://localhost:123#030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22"))));
        }
    }
}