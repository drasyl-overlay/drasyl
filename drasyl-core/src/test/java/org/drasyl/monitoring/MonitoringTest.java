package org.drasyl.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.pipeline.DrasylPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.SimplexDuplexHandler;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.drasyl.monitoring.Monitoring.MONITORING_HANDLER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitoringTest {
    @Mock
    private PeersManager peersManager;
    @Mock
    private Supplier<CompressedPublicKey> publicKeySupplier;
    @Mock
    private CompressedPublicKey publicKey;
    @Mock
    private DrasylPipeline pipeline;
    @Mock
    private AtomicBoolean opened;
    @Mock
    private Supplier<MeterRegistry> registrySupplier;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private MeterRegistry registry;
    @InjectMocks
    private Monitoring underTest;

    @Nested
    class Open {
        @Test
        void shouldSetOpenToTrue() {
            opened = new AtomicBoolean();
            when(registrySupplier.get()).thenReturn(registry);
            when(publicKeySupplier.get()).thenReturn(publicKey);
            underTest = new Monitoring(peersManager, publicKeySupplier, pipeline, registrySupplier, opened, registry);
            underTest.open();

            assertTrue(opened.get());
        }

        @Test
        void shouldAddHandlerToPipelineAndListenOnPeerRelayEvents(@Mock(answer = Answers.RETURNS_DEEP_STUBS) HandlerContext ctx) {
            when(registrySupplier.get()).thenReturn(registry);
            when(publicKeySupplier.get()).thenReturn(publicKey);
            when(pipeline.addFirst(eq(MONITORING_HANDLER), any())).then(invocation -> {
                SimplexDuplexHandler handler = invocation.getArgument(1);
                handler.eventTriggered(ctx, mock(Event.class));
                handler.read(ctx, mock(ApplicationMessage.class));
                handler.write(ctx, mock(ApplicationMessage.class), new CompletableFuture<>());
                return invocation.getMock();
            });

            underTest = new Monitoring(peersManager, publicKeySupplier, pipeline, registrySupplier, new AtomicBoolean(), registry);
            underTest.open();

            verify(pipeline).addFirst(eq(MONITORING_HANDLER), any());
            verify(ctx.scheduler(), times(3)).scheduleDirect(any());
        }
    }

    @Nested
    class Close {
        @Test
        void shouldSetOpenToFalse() {
            underTest = new Monitoring(peersManager, publicKeySupplier, pipeline, registrySupplier, new AtomicBoolean(true), registry);

            underTest.close();

            assertFalse(opened.get());
        }

        @Test
        void shouldRemoveHandlerFromPipeline() {
            underTest = new Monitoring(peersManager, publicKeySupplier, pipeline, registrySupplier, new AtomicBoolean(true), registry);

            underTest.close();

            verify(pipeline).remove(MONITORING_HANDLER);
        }
    }
}