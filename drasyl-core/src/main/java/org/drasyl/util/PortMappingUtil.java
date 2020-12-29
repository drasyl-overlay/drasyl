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
package org.drasyl.util;

import com.google.common.base.Suppliers;
import com.offbynull.portmapper.PortMapperFactory;
import com.offbynull.portmapper.gateway.Bus;
import com.offbynull.portmapper.gateways.network.NetworkGateway;
import com.offbynull.portmapper.gateways.process.ProcessGateway;
import com.offbynull.portmapper.mapper.MappedPort;
import com.offbynull.portmapper.mapper.PortMapper;
import com.offbynull.portmapper.mapper.PortType;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.plugin.groups.util.DurationUtil.max;
import static org.drasyl.plugin.groups.util.DurationUtil.min;

/**
 * Class for the creation of port mappings to make local services externally/publicly accessible.
 */
public class PortMappingUtil {
    private static final Logger LOG = LoggerFactory.getLogger(PortMappingUtil.class);
    private static final Duration ROUTERS_CACHE_TIME = ofMinutes(10);
    private static final Duration PORT_LIFETIME = ofMinutes(5);
    private static final Duration RETRY_DELAY = ofSeconds(10);
    private static Supplier<List<PortMapper>> mappersSupplier;

    private PortMappingUtil() {
        // util class
    }

    /**
     * Exposes {@code address} via PCP, NAT-PMP, or UPNP-IGD. This operation scans the local network
     * for relevant routers. If no devices are discovered or no port forwardings can be created,
     * this method will return an empty set.
     * <p>
     * Note: This is a blocking method, because it connects to other devices that may react slowly
     * or not at all.
     *
     * @param address  address to be exposed
     * @param protocol protocol to be exposed
     * @return List of port mappings.
     */
    public static Set<PortMapping> expose(final InetSocketAddress address,
                                          final Protocol protocol) {
        // discover available port mapping devices
        final List<PortMapper> mappers = discoverMappers(address.getAddress());

        final Set<PortMapping> mappings = new HashSet<>();
        for (final PortMapper mapper : mappers) {
            try {
                mappings.add(new PortMapping(mapper, address, protocol));
            }
            catch (final IllegalStateException e) {
                LOG.debug("Unable to expose port: ", e);
            }
        }

        return mappings;
    }

    /**
     * Searches for PCP, NAT-PMP, or UPNP-IGD enabled routers responsible for {@code address}.
     * <p>
     * Note: Because this is an expensive operation, the search result is cached for the time
     * defined in {@link #ROUTERS_CACHE_TIME}.
     *
     * @param address IP address for which routers will be searched for
     * @return list of routers that are in the same network as {@code address}.
     */
    private static synchronized List<PortMapper> discoverMappers(
            final InetAddress address) {
        if (mappersSupplier == null) {
            final Bus networkBus = NetworkGateway.create().getBus();
            final Bus processBus = ProcessGateway.create().getBus();
            mappersSupplier = Suppliers.memoizeWithExpiration(() -> {
                try {
                    LOG.debug("Search for PCP, NAT-PMP, or UPNP-IGD enabled routers on all available network interfaces...");
                    final List<PortMapper> mappers = PortMapperFactory.discover(networkBus, processBus);
                    LOG.debug("{} router(s) discovered.", mappers.size());
                    return mappers;
                }
                catch (final InterruptedException | IllegalStateException e) {
                    Thread.currentThread().interrupt();
                    return List.of();
                }
            }, ROUTERS_CACHE_TIME.toMillis(), MILLISECONDS);
        }

        // filter out mappers that are not in same network
        if (!address.isAnyLocalAddress()) {
            final short networkPrefixLength = NetworkUtil.getNetworkPrefixLength(address);
            return mappersSupplier.get()
                    .stream().filter(m -> NetworkUtil.sameNetwork(address, m.getSourceAddress(), networkPrefixLength))
                    .collect(Collectors.toList());
        }
        else {
            return mappersSupplier.get();
        }
    }

    public enum Protocol {
        UDP(PortType.UDP),
        TCP(PortType.TCP);
        private final PortType portType;

        Protocol(final PortType portType) {
            this.portType = portType;
        }

        PortType getPortType() {
            return portType;
        }
    }

    /**
     * Represents a port mapping.
     */
    public static class PortMapping {
        private final PortMapper mapper;
        private final InetSocketAddress address;
        private final Protocol protocol;
        private final Subject<Optional<InetSocketAddress>> externalAddressObservable;
        private final Scheduler scheduler;
        private MappedPort mappedPort;
        private Disposable refreshDisposable;

        PortMapping(final PortMapper mapper,
                    final InetSocketAddress address,
                    final Protocol protocol,
                    final Subject<Optional<InetSocketAddress>> externalAddressObservable,
                    final Scheduler scheduler,
                    final MappedPort mappedPort,
                    final Disposable refreshDisposable) {
            this.mapper = mapper;
            this.address = address;
            this.protocol = protocol;
            this.externalAddressObservable = externalAddressObservable;
            this.scheduler = scheduler;
            this.mappedPort = mappedPort;
            this.refreshDisposable = refreshDisposable;
        }

        PortMapping(final PortMapper mapper,
                    final InetSocketAddress address,
                    final Protocol protocol,
                    final Subject<Optional<InetSocketAddress>> externalAddressObservable,
                    final Scheduler scheduler) {
            this(mapper, address, protocol, externalAddressObservable, scheduler, null, null);

            try {
                createMapping();
            }
            catch (final IllegalStateException e) {
                throw new IllegalStateException(mappingMethod() + " router " + mapper.getSourceAddress() + " was unable to create port mapping for " + address + ": " + e.getMessage());
            }
        }

        /**
         * Attempts to create a port mapping for {@code address} at router {@code mapper}.
         *
         * @param mapper   router where the port mapping will be requested.
         * @param address  address to be exposed
         * @param protocol protocol to be exposed
         * @throws IllegalStateException if the port could not be mapped for any reason
         */
        public PortMapping(final PortMapper mapper,
                           final InetSocketAddress address,
                           final Protocol protocol) {
            this(mapper, address, protocol, BehaviorSubject.createDefault(Optional.empty()), DrasylScheduler.getInstanceHeavy());
        }

        /**
         * Closes this port mapping.
         */
        public synchronized void close() {
            if (mappedPort != null) {
                LOG.debug("Close {} port mapping {} -> {} on {} router", mappedPort::getPortType, this::currentExternalAddress, () -> address, this::mappingMethod);
                try {
                    refreshDisposable.dispose();
                    refreshDisposable = null;
                    mapper.unmapPort(mappedPort);
                }
                catch (final IllegalStateException e) {
                    LOG.debug("Unable to close port mapping on {} router for {}: {}", this::mappingMethod, () -> address, e::getMessage);
                }
                catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                finally {
                    externalAddressObservable.onNext(Optional.empty());
                    externalAddressObservable.onComplete();
                    mappedPort = null;
                }
            }
        }

        /**
         * Returns the external address of the port mapping.
         *
         * @return external address or {@code null} if mapping has been closed
         */
        public InetSocketAddress currentExternalAddress() {
            return externalAddressObservable.blockingFirst().orElse(null);
        }

        private String mappingMethod() {
            return mapper.getClass().getSimpleName().replace("PortMapper", "");
        }

        @Override
        public String toString() {
            return "PortMapping{" +
                    "address=" + address +
                    ", currentExternalAddress=" + currentExternalAddress() +
                    ", mappingMethod=" + mappingMethod() +
                    '}';
        }

        /**
         * Returns an {@link Observable} which emits the current and every future external
         * addresses. Can be used to observe this port mapping for address changes made by the
         * exposing router.
         *
         * @return {@link Observable} with current and future external addresses
         */
        public Observable<Optional<InetSocketAddress>> externalAddress() {
            return externalAddressObservable.distinctUntilChanged().subscribeOn(scheduler);
        }

        private void scheduleMappingRefresh(final Duration delay) {
            refreshDisposable = scheduler.scheduleDirect(() -> {
                try {
                    createMapping();
                }
                catch (final IllegalStateException e) {
                    LOG.debug("Unable to refresh {} port mapping on {} router for {}. Retry in {}ms", mappedPort::getPortType, this::mappingMethod, () -> address, RETRY_DELAY::toMillis);
                    externalAddressObservable.onNext(Optional.empty());
                    scheduleMappingRefresh(RETRY_DELAY);
                }
            }, delay.toMillis(), MILLISECONDS);
        }

        private void createMapping() {
            try {
                mappedPort = mapper.mapPort(protocol.getPortType(), address.getPort(), address.getPort(), PORT_LIFETIME.getSeconds());
                final InetSocketAddress externalAdded = new InetSocketAddress(mappedPort.getExternalAddress().getHostName(), mappedPort.getExternalPort());
                LOG.debug("{} router has created {} port mapping {} -> {} (lifetime: {}s)", this::mappingMethod, mappedPort::getPortType, () -> externalAdded, () -> address, mappedPort::getLifetime);
                externalAddressObservable.onNext(Optional.of(externalAdded));

                scheduleMappingRefresh(min(max(ofSeconds(mappedPort.getLifetime()).dividedBy(2), RETRY_DELAY), PORT_LIFETIME));
            }
            catch (final IllegalArgumentException | NullPointerException e) {
                // can occur when router returns non-positive mapping lifetime or other unexpected answers
                throw new IllegalStateException(e);
            }
            catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}