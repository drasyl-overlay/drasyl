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
package org.drasyl.localhost;

import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.serialization.SerializedApplicationMessage;
import org.drasyl.remote.protocol.AddressedIntermediateEnvelope;
import org.drasyl.util.ThrowingBiConsumer;
import org.drasyl.util.scheduler.DrasylScheduler;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalHostDiscoveryTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    private final Duration leaseTime = ofSeconds(60);
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Path discoveryPath;
    @Mock
    private CompressedPublicKey ownPublicKey;
    @Mock
    private ThrowingBiConsumer<File, Object, IOException> jacksonWriter;
    private final Map<CompressedPublicKey, InetSocketAddressWrapper> routes = new HashMap<>();
    @Mock
    private Disposable watchDisposable;
    @Mock
    private Disposable postDisposable;

    @Nested
    class StartDiscovery {
        @Test
        void shouldStartDiscoveryOnNodeUpEvent(@Mock(answer = RETURNS_DEEP_STUBS) final NodeUpEvent event,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final DrasylScheduler dependentScheduler,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final DrasylScheduler independentScheduler) {
            when(dependentScheduler.scheduleDirect(any())).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(independentScheduler.scheduleDirect(any())).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(discoveryPath.toFile().exists()).thenReturn(true);
            when(discoveryPath.toFile().isDirectory()).thenReturn(true);
            when(discoveryPath.toFile().canRead()).thenReturn(true);
            when(discoveryPath.toFile().canWrite()).thenReturn(true);
            when(config.getRemoteLocalHostDiscoveryLeaseTime()).thenReturn(leaseTime);
            when(config.getRemoteLocalHostDiscoveryPath().resolve(any(String.class))).thenReturn(discoveryPath);

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, dependentScheduler, independentScheduler, handler)) {
                pipeline.processInbound(event).join();

                verify(dependentScheduler, timeout(1_000)).schedulePeriodicallyDirect(any(), anyLong(), eq(5_000L), eq(MILLISECONDS));
            }
        }

        @Test
        void shouldTryToRegisterAWatchService(@Mock(answer = RETURNS_DEEP_STUBS) final NodeUpEvent event) throws IOException {
            when(discoveryPath.toFile().exists()).thenReturn(true);
            when(discoveryPath.toFile().isDirectory()).thenReturn(true);
            when(discoveryPath.toFile().canRead()).thenReturn(true);
            when(discoveryPath.toFile().canWrite()).thenReturn(true);
            when(config.getRemoteLocalHostDiscoveryPath().resolve(any(String.class))).thenReturn(discoveryPath);

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                pipeline.processInbound(event).join();

                verify(discoveryPath, timeout(1_000)).register(any(), eq(ENTRY_CREATE), eq(ENTRY_MODIFY), eq(ENTRY_DELETE));
            }
        }

        @Test
        void shouldScheduleTasksForPollingWatchServiceAndPostingOwnInformation(@Mock(answer = RETURNS_DEEP_STUBS) final NodeUpEvent event,
                                                                               @Mock(answer = RETURNS_DEEP_STUBS) final DrasylScheduler dependentScheduler,
                                                                               @Mock(answer = RETURNS_DEEP_STUBS) final DrasylScheduler independentScheduler) {
            when(dependentScheduler.scheduleDirect(any())).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(independentScheduler.scheduleDirect(any())).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(discoveryPath.toFile().exists()).thenReturn(true);
            when(discoveryPath.toFile().isDirectory()).thenReturn(true);
            when(discoveryPath.toFile().canRead()).thenReturn(true);
            when(discoveryPath.toFile().canWrite()).thenReturn(true);
            when(config.getRemoteLocalHostDiscoveryLeaseTime()).thenReturn(leaseTime);
            when(config.getRemoteLocalHostDiscoveryPath().resolve(any(String.class))).thenReturn(discoveryPath);

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, dependentScheduler, independentScheduler, handler)) {
                pipeline.processInbound(event).join();

                verify(dependentScheduler, timeout(1_000)).schedulePeriodicallyDirect(any(), anyLong(), eq(5_000L), eq(MILLISECONDS));
                verify(dependentScheduler, timeout(1_000)).schedulePeriodicallyDirect(any(), anyLong(), eq(55_000L), eq(MILLISECONDS));
            }
        }

        @Test
        void scheduledTasksShouldPollWatchServiceAndPostOwnInformationToFileSystem(@TempDir final Path dir,
                                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final NodeUpEvent event,
                                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final FileSystem fileSystem,
                                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final WatchService watchService,
                                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final DrasylScheduler dependentScheduler,
                                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final DrasylScheduler independentScheduler) throws IOException {
            final Path path = Paths.get(dir.toString(), "03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a.json");
            when(discoveryPath.toFile().exists()).thenReturn(true);
            when(discoveryPath.toFile().isDirectory()).thenReturn(true);
            when(discoveryPath.toFile().canRead()).thenReturn(true);
            when(discoveryPath.toFile().canWrite()).thenReturn(true);
            when(discoveryPath.resolve(anyString())).thenReturn(path);
            when(dependentScheduler.scheduleDirect(any())).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(independentScheduler.scheduleDirect(any())).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(dependentScheduler.schedulePeriodicallyDirect(any(), anyLong(), eq(5_000L), eq(MILLISECONDS))).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(dependentScheduler.schedulePeriodicallyDirect(any(), anyLong(), eq(55_000L), eq(MILLISECONDS))).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(config.getRemoteLocalHostDiscoveryLeaseTime()).thenReturn(leaseTime);
            when(identity.getPublicKey()).thenReturn(ownPublicKey);
            when(config.getRemoteLocalHostDiscoveryPath().resolve(any(String.class))).thenReturn(discoveryPath);
            when(discoveryPath.getFileSystem()).thenReturn(fileSystem);
            when(fileSystem.newWatchService()).thenReturn(watchService);

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, dependentScheduler, independentScheduler, handler)) {
                pipeline.processInbound(event).join();

                verify(watchService, timeout(1_000)).poll();
                verify(jacksonWriter, timeout(10_000)).accept(eq(path.toFile()), any());
            }
        }
    }

    @Nested
    class StopDiscovery {
        @Test
        void shouldStopDiscoveryOnNodeUnrecoverableErrorEvent(@Mock final NodeUnrecoverableErrorEvent event,
                                                              @Mock final CompressedPublicKey publicKey,
                                                              @Mock final InetSocketAddressWrapper address) {
            routes.put(publicKey, address);

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                pipeline.processInbound(event).join();

                verify(watchDisposable, timeout(1_000)).dispose();
                verify(postDisposable, timeout(1_000)).dispose();
                await().untilAsserted(() -> assertTrue(routes.isEmpty()));
            }
        }

        @Test
        void shouldStopDiscoveryOnNodeDownEvent(@Mock final NodeDownEvent event,
                                                @Mock final CompressedPublicKey publicKey,
                                                @Mock final InetSocketAddressWrapper address) {
            routes.put(publicKey, address);

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                pipeline.processInbound(event).join();

                verify(watchDisposable, timeout(1_000)).dispose();
                verify(postDisposable, timeout(1_000)).dispose();
                await().untilAsserted(() -> assertTrue(routes.isEmpty()));
            }
        }
    }

    @Nested
    class MessagePassing {
        @SuppressWarnings("rawtypes")
        @Test
        void shouldRouteOutboundMessageWhenStaticRouteIsPresent(@Mock(answer = RETURNS_DEEP_STUBS) final SerializedApplicationMessage message) {
            final InetSocketAddressWrapper address = new InetSocketAddressWrapper(22527);
            final CompressedPublicKey publicKey = CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
            routes.put(publicKey, address);
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(1));
            when(message.getRecipient()).thenReturn(publicKey);
            when(message.getType()).thenReturn(byte[].class.getName());
            when(message.getContent()).thenReturn(new byte[0]);

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            final TestObserver<AddressedIntermediateEnvelope> outboundMessages;
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                outboundMessages = pipeline.outboundMessages(AddressedIntermediateEnvelope.class).test();

                pipeline.processOutbound(publicKey, message).join();

                outboundMessages.awaitCount(1)
                        .assertValueAt(0, m -> m.getRecipient().equals(address));
            }
        }

        @Test
        void shouldPassthroughMessageWhenStaticRouteIsAbsent(@Mock final CompressedPublicKey publicKey,
                                                             @Mock(answer = RETURNS_DEEP_STUBS) final SerializedApplicationMessage message) {
            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            final TestObserver<SerializedApplicationMessage> outboundMessages;
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                outboundMessages = pipeline.outboundMessages(SerializedApplicationMessage.class).test();

                pipeline.processOutbound(publicKey, message).join();

                outboundMessages.awaitCount(1);
            }
        }
    }

    @Nested
    class Scan {
        @Test
        void shouldScanDirectory(@TempDir final Path dir,
                                 @Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                 @Mock final InetSocketAddressWrapper address) throws IOException {
            final CompressedPublicKey publicKey = CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
            routes.put(publicKey, address);

            when(ctx.config().getRemoteLocalHostDiscoveryPath()).thenReturn(dir);
            when(ctx.config().getRemoteLocalHostDiscoveryLeaseTime()).thenReturn(ofMinutes(5));
            final Path path = Paths.get(dir.toString(), "0", "03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a.json");
            Files.createDirectory(path.getParent());
            Files.writeString(path, "[\"192.168.188.42:12345\",\"192.168.188.23:12345\"]", StandardOpenOption.CREATE);

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            handler.scan(ctx);

            assertEquals(Map.of(
                    CompressedPublicKey.of("03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a"),
                    new InetSocketAddressWrapper("192.168.188.23", 12345)
            ), routes);

            verify(ctx.peersManager()).addPath(eq(CompressedPublicKey.of("03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a")), any());
        }
    }
}
