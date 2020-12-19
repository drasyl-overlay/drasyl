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
package org.drasyl.monitoring;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.influx.InfluxConfig;
import io.micrometer.influx.InfluxMeterRegistry;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.drasyl.util.NetworkUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

/**
 * Monitors various states or events in the drasyl node.
 */
@SuppressWarnings({ "java:S110" })
public class Monitoring extends SimpleDuplexHandler<Object, Object, Address> {
    public static final Monitoring INSTANCE = new Monitoring();
    public static final String MONITORING_HANDLER = "MONITORING_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(Monitoring.class);
    private final Map<String, Counter> counters;
    private final Function<HandlerContext, MeterRegistry> registrySupplier;
    private MeterRegistry registry;

    Monitoring(final Map<String, Counter> counters,
               final Function<HandlerContext, MeterRegistry> registrySupplier,
               final MeterRegistry registry) {
        this.counters = requireNonNull(counters);
        this.registrySupplier = requireNonNull(registrySupplier);
        this.registry = registry;
    }

    private Monitoring() {
        this(
                new HashMap<>(),
                ctx -> {
                    final MeterRegistry newRegistry = new InfluxMeterRegistry(new InfluxConfig() {
                        @Override
                        public @NotNull String uri() {
                            return ctx.config().getMonitoringInfluxUri().toString();
                        }

                        @Override
                        public String userName() {
                            return ctx.config().getMonitoringInfluxUser();
                        }

                        @Override
                        public String password() {
                            return ctx.config().getMonitoringInfluxPassword();
                        }

                        @Override
                        public @NotNull String db() {
                            return ctx.config().getMonitoringInfluxDatabase();
                        }

                        @Override
                        public boolean autoCreateDb() {
                            return false;
                        }

                        @Override
                        public @NotNull Duration step() {
                            return ctx.config().getMonitoringInfluxReportingFrequency();
                        }

                        @Override
                        public String get(final @NotNull String key) {
                            return null;
                        }
                    }, Clock.SYSTEM);

                    // add common tags
                    final String hostTag;
                    if (!ctx.config().getMonitoringHostTag().isEmpty()) {
                        hostTag = ctx.config().getMonitoringHostTag();
                    }
                    else {
                        hostTag = ofNullable(NetworkUtil.getLocalHostName()).orElse("");
                    }

                    newRegistry.config().commonTags(
                            "public_key", ctx.identity().getPublicKey().toString(),
                            "host", hostTag
                    );

                    // monitor PeersManager
                    Gauge.builder("peersManager.peers", ctx.peersManager(), pm -> pm.getPeers().size()).register(newRegistry);
                    Gauge.builder("peersManager.superPeer", ctx.peersManager(), pm -> pm.getSuperPeerKey() != null ? 1 : 0).register(newRegistry);
                    Gauge.builder("peersManager.children", ctx.peersManager(), pm -> pm.getChildrenKeys().size()).register(newRegistry);

                    return newRegistry;
                },
                null
        );
    }

    @Override
    public void eventTriggered(final HandlerContext ctx,
                               final Event event,
                               final CompletableFuture<Void> future) {
        ctx.scheduler().scheduleDirect(() -> incrementObjectTypeCounter("pipeline.events", event));

        if (event instanceof NodeUpEvent) {
            startMonitoring(ctx);
        }
        else if (event instanceof NodeUnrecoverableErrorEvent || event instanceof NodeDownEvent) {
            stopMonitoring();
        }

        // passthrough event
        ctx.fireEventTriggered(event, future);
    }

    @Override
    protected void matchedRead(final HandlerContext ctx,
                               final Address sender,
                               final Object msg,
                               final CompletableFuture<Void> future) {
        ctx.scheduler().scheduleDirect(() -> incrementObjectTypeCounter("pipeline.inbound_messages", msg));

        // passthrough message
        ctx.fireRead(sender, msg, future);
    }

    @Override
    protected void matchedWrite(final HandlerContext ctx,
                                final Address recipient,
                                final Object msg,
                                final CompletableFuture<Void> future) {
        ctx.scheduler().scheduleDirect(() -> incrementObjectTypeCounter("pipeline.outbound_messages", msg));

        // passthrough message
        ctx.write(recipient, msg, future);
    }

    synchronized void startMonitoring(final HandlerContext ctx) {
        if (registry == null) {
            LOG.debug("Start Monitoring...");
            registry = registrySupplier.apply(ctx);

            LOG.debug("Monitoring started.");
        }
    }

    synchronized void stopMonitoring() {
        if (registry != null) {
            LOG.debug("Stop Monitoring...");
            registry.close();
            registry = null;
            LOG.debug("Monitoring stopped.");
        }
    }

    private void incrementObjectTypeCounter(final String metric, final Object o) {
        if (registry != null) {
            final Counter counter = counters.computeIfAbsent(o.getClass().getSimpleName(), clazz -> Counter.builder(metric).tag("clazz", clazz).register(registry));
            counter.increment();
        }
    }
}
