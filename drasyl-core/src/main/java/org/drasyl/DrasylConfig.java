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
package org.drasyl;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.Endpoint;
import org.drasyl.plugin.DrasylPlugin;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.drasyl.util.SecretUtil.maskSecret;

/**
 * This class represents the configuration for a {@link DrasylNode}. For example, it defines the
 * identity and the Super Peer.
 * <p>
 * This is an immutable object.
 */
public class DrasylConfig {
    static final DrasylConfig DEFAULT = new DrasylConfig(ConfigFactory.defaultReference());
    //======================================== Config Paths ========================================
    static final String NETWORK_ID = "drasyl.network.id";
    static final String IDENTITY_PROOF_OF_WORK = "drasyl.identity.proof-of-work";
    static final String IDENTITY_PUBLIC_KEY = "drasyl.identity.public-key";
    static final String IDENTITY_PRIVATE_KEY = "drasyl.identity.private-key";
    static final String IDENTITY_PATH = "drasyl.identity.path";
    static final String REMOTE_ENABLED = "drasyl.remote.enabled";
    static final String REMOTE_BIND_HOST = "drasyl.remote.bind-host";
    static final String REMOTE_BIND_PORT = "drasyl.remote.bind-port";
    static final String REMOTE_ENDPOINTS = "drasyl.remote.endpoints";
    static final String REMOTE_EXPOSE_ENABLED = "drasyl.remote.expose.enabled";
    static final String REMOTE_PING_INTERVAL = "drasyl.remote.ping.interval";
    static final String REMOTE_PING_TIMEOUT = "drasyl.remote.ping.timeout";
    static final String REMOTE_PING_COMMUNICATION_TIMEOUT = "drasyl.remote.ping.communication-timeout";
    static final String REMOTE_PING_MAX_PEERS = "drasyl.remote.ping.max-peers";
    static final String REMOTE_UNITE_MIN_INTERVAL = "drasyl.remote.unite.min-interval";
    static final String REMOTE_SUPER_PEER_ENABLED = "drasyl.remote.super-peer.enabled";
    static final String REMOTE_SUPER_PEER_ENDPOINT = "drasyl.remote.super-peer.endpoint";
    static final String REMOTE_MESSAGE_MTU = "drasyl.remote.message.mtu";
    static final String REMOTE_MESSAGE_MAX_CONTENT_LENGTH = "drasyl.remote.message.max-content-length";
    static final String REMOTE_MESSAGE_HOP_LIMIT = "drasyl.remote.message.hop-limit";
    static final String REMOTE_MESSAGE_COMPOSED_MESSAGE_TRANSFER_TIMEOUT = "drasyl.remote.message.composed-message-transfer-timeout";
    static final String INTRA_VM_DISCOVERY_ENABLED = "drasyl.intra-vm-discovery.enabled";
    static final String LOCAL_HOST_DISCOVERY_ENABLED = "drasyl.local-host-discovery.enabled";
    static final String LOCAL_HOST_DISCOVERY_PATH = "drasyl.local-host-discovery.path";
    static final String LOCAL_HOST_DISCOVERY_LEASE_TIME = "drasyl.local-host-discovery.lease-time";
    static final String MONITORING_ENABLED = "drasyl.monitoring.enabled";
    static final String MONITORING_HOST_TAG = "drasyl.monitoring.host-tag";
    static final String MONITORING_INFLUX_URI = "drasyl.monitoring.influx.uri";
    static final String MONITORING_INFLUX_USER = "drasyl.monitoring.influx.user";
    static final String MONITORING_INFLUX_PASSWORD = "drasyl.monitoring.influx.password";
    static final String MONITORING_INFLUX_DATABASE = "drasyl.monitoring.influx.database";
    static final String MONITORING_INFLUX_REPORTING_FREQUENCY = "drasyl.monitoring.influx.reporting-frequency";
    static final String PLUGINS = "drasyl.plugins";
    static final String MARSHALLING_INBOUND_ALLOWED_TYPES = "drasyl.marshalling.inbound.allowed-types";
    static final String MARSHALLING_INBOUND_ALLOW_ALL_PRIMITIVES = "drasyl.marshalling.inbound.allow-all-primitives";
    static final String MARSHALLING_INBOUND_ALLOW_ARRAY_OF_DEFINED_TYPES = "drasyl.marshalling.inbound.allow-array-of-defined-types";
    static final String MARSHALLING_INBOUND_ALLOWED_PACKAGES = "drasyl.marshalling.inbound.allowed-packages";
    static final String MARSHALLING_OUTBOUND_ALLOWED_TYPES = "drasyl.marshalling.outbound.allowed-types";
    static final String MARSHALLING_OUTBOUND_ALLOW_ALL_PRIMITIVES = "drasyl.marshalling.outbound.allow-all-primitives";
    static final String MARSHALLING_OUTBOUND_ALLOW_ARRAY_OF_DEFINED_TYPES = "drasyl.marshalling.outbound.allow-array-of-defined-types";
    static final String MARSHALLING_OUTBOUND_ALLOWED_PACKAGES = "drasyl.marshalling.outbound.allowed-packages";
    //======================================= Config Values ========================================
    private final int networkId;
    private final ProofOfWork identityProofOfWork;
    private final CompressedPublicKey identityPublicKey;
    private final CompressedPrivateKey identityPrivateKey;
    private final Path identityPath;
    private final InetAddress remoteBindHost;
    private final boolean remoteEnabled;
    private final int remoteBindPort;
    private final Duration remotePingInterval;
    private final Duration remotePingTimeout;
    private final Duration remotePingCommunicationTimeout;
    private final Duration remoteUniteMinInterval;
    private final int remotePingMaxPeers;
    private final Set<Endpoint> remoteEndpoints;
    private final boolean remoteExposeEnabled;
    private final int remoteMessageMtu;
    private final int remoteMessageMaxContentLength;
    private final byte remoteMessageHopLimit;
    private final Duration remoteMessageComposedMessageTransferTimeout;
    private final boolean remoteSuperPeerEnabled;
    private final Endpoint remoteSuperPeerEndpoint;
    private final boolean intraVmDiscoveryEnabled;
    private final boolean localHostDiscoveryEnabled;
    private final Path localHostDiscoveryPath;
    private final Duration localHostDiscoveryLeaseTime;
    private final boolean monitoringEnabled;
    private final String monitoringHostTag;
    private final URI monitoringInfluxUri;
    private final String monitoringInfluxUser;
    private final String monitoringInfluxPassword;
    private final String monitoringInfluxDatabase;
    private final Duration monitoringInfluxReportingFrequency;
    private final Set<DrasylPlugin> pluginSet;
    private final List<String> marshallingInboundAllowedTypes;
    private final boolean marshallingInboundAllowAllPrimitives;
    private final boolean marshallingInboundAllowArrayOfDefinedTypes;
    private final List<String> marshallingInboundAllowedPackages;
    private final List<String> marshallingOutboundAllowedTypes;
    private final boolean marshallingOutboundAllowAllPrimitives;
    private final boolean marshallingOutboundAllowArrayOfDefinedTypes;
    private final List<String> marshallingOutboundAllowedPackages;

    public DrasylConfig() {
        this(ConfigFactory.load());
    }

    /**
     * Creates a new config for a drasyl node.
     *
     * @param config config to be loaded
     */
    public DrasylConfig(final Config config) {
        config.checkValid(ConfigFactory.defaultReference(), "drasyl");

        this.networkId = config.getInt(NETWORK_ID);

        // init identity config
        if (config.getInt(IDENTITY_PROOF_OF_WORK) >= 0) {
            this.identityProofOfWork = getProofOfWork(config, IDENTITY_PROOF_OF_WORK);
        }
        else {
            this.identityProofOfWork = null;
        }
        if (!config.getString(IDENTITY_PUBLIC_KEY).isEmpty()) {
            this.identityPublicKey = getPublicKey(config, IDENTITY_PUBLIC_KEY);
        }
        else {
            this.identityPublicKey = null;
        }
        if (!config.getString(IDENTITY_PRIVATE_KEY).isEmpty()) {
            this.identityPrivateKey = getPrivateKey(config, IDENTITY_PRIVATE_KEY);
        }
        else {
            this.identityPrivateKey = null;
        }
        this.identityPath = getPath(config, IDENTITY_PATH);

        // Init remote config
        this.remoteEnabled = config.getBoolean(REMOTE_ENABLED);
        this.remoteBindHost = getInetAddress(config, REMOTE_BIND_HOST);
        this.remoteBindPort = config.getInt(REMOTE_BIND_PORT);
        this.remoteEndpoints = Set.copyOf(getEndpointList(config, REMOTE_ENDPOINTS));
        this.remoteExposeEnabled = config.getBoolean(REMOTE_EXPOSE_ENABLED);
        this.remotePingInterval = config.getDuration(REMOTE_PING_INTERVAL);
        this.remotePingTimeout = config.getDuration(REMOTE_PING_TIMEOUT);
        this.remotePingCommunicationTimeout = config.getDuration(REMOTE_PING_COMMUNICATION_TIMEOUT);
        this.remotePingMaxPeers = config.getInt(REMOTE_PING_MAX_PEERS);
        this.remoteUniteMinInterval = config.getDuration(REMOTE_UNITE_MIN_INTERVAL);
        this.remoteSuperPeerEnabled = config.getBoolean(REMOTE_SUPER_PEER_ENABLED);
        this.remoteSuperPeerEndpoint = getEndpoint(config, REMOTE_SUPER_PEER_ENDPOINT);
        this.remoteMessageMtu = (int) Math.min(config.getMemorySize(REMOTE_MESSAGE_MTU).toBytes(), Integer.MAX_VALUE);
        this.remoteMessageMaxContentLength = (int) Math.min(config.getMemorySize(REMOTE_MESSAGE_MAX_CONTENT_LENGTH).toBytes(), Integer.MAX_VALUE);
        this.remoteMessageComposedMessageTransferTimeout = config.getDuration(REMOTE_MESSAGE_COMPOSED_MESSAGE_TRANSFER_TIMEOUT);
        this.remoteMessageHopLimit = getByte(config, REMOTE_MESSAGE_HOP_LIMIT);

        this.intraVmDiscoveryEnabled = config.getBoolean(INTRA_VM_DISCOVERY_ENABLED);

        // Init local host discovery config
        this.localHostDiscoveryEnabled = config.getBoolean(LOCAL_HOST_DISCOVERY_ENABLED);
        if (!config.getString(LOCAL_HOST_DISCOVERY_PATH).equals("")) {
            this.localHostDiscoveryPath = getPath(config, LOCAL_HOST_DISCOVERY_PATH);
        }
        else {
            this.localHostDiscoveryPath = Paths.get(System.getProperty("java.io.tmpdir"), "drasyl-discovery");
        }
        this.localHostDiscoveryLeaseTime = config.getDuration(LOCAL_HOST_DISCOVERY_LEASE_TIME);

        // Init monitoring config
        monitoringEnabled = config.getBoolean(MONITORING_ENABLED);
        monitoringHostTag = config.getString(MONITORING_HOST_TAG);
        monitoringInfluxUri = getURI(config, MONITORING_INFLUX_URI);
        monitoringInfluxUser = config.getString(MONITORING_INFLUX_USER);
        monitoringInfluxPassword = config.getString(MONITORING_INFLUX_PASSWORD);
        monitoringInfluxDatabase = config.getString(MONITORING_INFLUX_DATABASE);
        monitoringInfluxReportingFrequency = config.getDuration(MONITORING_INFLUX_REPORTING_FREQUENCY);

        // Load plugins
        this.pluginSet = Set.copyOf(getPlugins(config, PLUGINS));

        // Load marshalling config
        this.marshallingInboundAllowedTypes = List.copyOf(config.getStringList(MARSHALLING_INBOUND_ALLOWED_TYPES));
        this.marshallingInboundAllowAllPrimitives = config.getBoolean(MARSHALLING_INBOUND_ALLOW_ALL_PRIMITIVES);
        this.marshallingInboundAllowArrayOfDefinedTypes = config.getBoolean(MARSHALLING_INBOUND_ALLOW_ARRAY_OF_DEFINED_TYPES);
        this.marshallingInboundAllowedPackages = List.copyOf(config.getStringList(MARSHALLING_INBOUND_ALLOWED_PACKAGES));
        this.marshallingOutboundAllowedTypes = List.copyOf(config.getStringList(MARSHALLING_OUTBOUND_ALLOWED_TYPES));
        this.marshallingOutboundAllowAllPrimitives = config.getBoolean(MARSHALLING_OUTBOUND_ALLOW_ALL_PRIMITIVES);
        this.marshallingOutboundAllowArrayOfDefinedTypes = config.getBoolean(MARSHALLING_OUTBOUND_ALLOW_ARRAY_OF_DEFINED_TYPES);
        this.marshallingOutboundAllowedPackages = List.copyOf(config.getStringList(MARSHALLING_OUTBOUND_ALLOWED_PACKAGES));
    }

    @SuppressWarnings({ "java:S107" })
    DrasylConfig(final int networkId,
                 final ProofOfWork identityProofOfWork,
                 final CompressedPublicKey identityPublicKey,
                 final CompressedPrivateKey identityPrivateKey,
                 final Path identityPath,
                 final InetAddress remoteBindHost,
                 final boolean remoteEnabled,
                 final int remoteBindPort,
                 final Duration remotePingInterval,
                 final Duration remotePingTimeout,
                 final Duration remotePingCommunicationTimeout,
                 final Duration remoteUniteMinInterval,
                 final int remotePingMaxPeers,
                 final Set<Endpoint> remoteEndpoints,
                 final boolean remoteExposeEnabled,
                 final boolean remoteSuperPeerEnabled,
                 final Endpoint remoteSuperPeerEndpoint,
                 final int remoteMessageMaxContentLength,
                 final byte remoteMessageHopLimit,
                 final Duration remoteMessageComposedMessageTransferTimeout,
                 final int remoteMessageMtu,
                 final boolean intraVmDiscoveryEnabled,
                 final boolean localHostDiscoveryEnabled,
                 final Path localHostDiscoveryPath,
                 final Duration localHostDiscoveryLeaseTime,
                 final boolean monitoringEnabled,
                 final String monitoringHostTag,
                 final URI monitoringInfluxUri,
                 final String monitoringInfluxUser,
                 final String monitoringInfluxPassword,
                 final String monitoringInfluxDatabase,
                 final Duration monitoringInfluxReportingFrequency,
                 final Set<DrasylPlugin> pluginSet,
                 final List<String> marshallingInboundAllowedTypes,
                 final boolean marshallingInboundAllowAllPrimitives,
                 final boolean marshallingInboundAllowArrayOfDefinedTypes,
                 final List<String> marshallingInboundAllowedPackages,
                 final List<String> marshallingOutboundAllowedTypes,
                 final boolean marshallingOutboundAllowAllPrimitives,
                 final boolean marshallingOutboundAllowArrayOfDefinedTypes,
                 final List<String> marshallingOutboundAllowedPackages) {
        this.networkId = networkId;
        this.identityProofOfWork = identityProofOfWork;
        this.identityPublicKey = identityPublicKey;
        this.identityPrivateKey = identityPrivateKey;
        this.identityPath = identityPath;
        this.remoteBindHost = remoteBindHost;
        this.remoteEnabled = remoteEnabled;
        this.remoteBindPort = remoteBindPort;
        this.remotePingInterval = remotePingInterval;
        this.remotePingTimeout = remotePingTimeout;
        this.remotePingCommunicationTimeout = remotePingCommunicationTimeout;
        this.remoteUniteMinInterval = remoteUniteMinInterval;
        this.remotePingMaxPeers = remotePingMaxPeers;
        this.remoteEndpoints = remoteEndpoints;
        this.remoteExposeEnabled = remoteExposeEnabled;
        this.remoteSuperPeerEnabled = remoteSuperPeerEnabled;
        this.remoteSuperPeerEndpoint = remoteSuperPeerEndpoint;
        this.remoteMessageMtu = remoteMessageMtu;
        this.remoteMessageMaxContentLength = remoteMessageMaxContentLength;
        this.remoteMessageHopLimit = remoteMessageHopLimit;
        this.remoteMessageComposedMessageTransferTimeout = remoteMessageComposedMessageTransferTimeout;
        this.intraVmDiscoveryEnabled = intraVmDiscoveryEnabled;
        this.localHostDiscoveryEnabled = localHostDiscoveryEnabled;
        this.localHostDiscoveryPath = localHostDiscoveryPath;
        this.localHostDiscoveryLeaseTime = localHostDiscoveryLeaseTime;
        this.monitoringEnabled = monitoringEnabled;
        this.monitoringHostTag = monitoringHostTag;
        this.monitoringInfluxUri = monitoringInfluxUri;
        this.monitoringInfluxUser = monitoringInfluxUser;
        this.monitoringInfluxPassword = monitoringInfluxPassword;
        this.monitoringInfluxDatabase = monitoringInfluxDatabase;
        this.monitoringInfluxReportingFrequency = monitoringInfluxReportingFrequency;
        this.pluginSet = pluginSet;
        this.marshallingInboundAllowedTypes = marshallingInboundAllowedTypes;
        this.marshallingInboundAllowAllPrimitives = marshallingInboundAllowAllPrimitives;
        this.marshallingInboundAllowArrayOfDefinedTypes = marshallingInboundAllowArrayOfDefinedTypes;
        this.marshallingInboundAllowedPackages = marshallingInboundAllowedPackages;
        this.marshallingOutboundAllowedTypes = marshallingOutboundAllowedTypes;
        this.marshallingOutboundAllowAllPrimitives = marshallingOutboundAllowAllPrimitives;
        this.marshallingOutboundAllowArrayOfDefinedTypes = marshallingOutboundAllowArrayOfDefinedTypes;
        this.marshallingOutboundAllowedPackages = marshallingOutboundAllowedPackages;
    }

    /**
     * Gets the {@link ProofOfWork} at the given path. Similar to {@link Config}, an exception is
     * thrown for an invalid value.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the {@link ProofOfWork} value at the requested path
     * @throws ConfigException.Missing if value is absent or null
     */
    @SuppressWarnings({ "java:S1192" })
    public static ProofOfWork getProofOfWork(final Config config, final String path) {
        final int intValue = config.getInt(path);
        return ProofOfWork.of(intValue);
    }

    /**
     * Gets the {@link CompressedPublicKey} at the given path. Similar to {@link Config}, an
     * exception is thrown for an invalid value.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the {@link CompressedPublicKey} value at the requested path
     * @throws ConfigException.Missing   if value is absent or null
     * @throws ConfigException.WrongType if value is not convertible to a {@link CompressedPublicKey}
     */
    @SuppressWarnings({ "java:S1192" })
    public static CompressedPublicKey getPublicKey(final Config config, final String path) {
        try {
            final String stringValue = config.getString(path);
            return CompressedPublicKey.of(stringValue);
        }
        catch (final CryptoException | IllegalArgumentException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "compressed public key", "invalid-value: " + e.getMessage());
        }
    }

    /**
     * Gets the {@link CompressedPrivateKey} at the given path. Similar to {@link Config}, an
     * exception is thrown for an invalid value.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the {@link CompressedPrivateKey} value at the requested path
     * @throws ConfigException.Missing   if value is absent or null
     * @throws ConfigException.WrongType if value is not convertible to a {@link CompressedPrivateKey}
     */
    @SuppressWarnings({ "java:S1192" })
    public static CompressedPrivateKey getPrivateKey(final Config config, final String path) {
        try {
            final String stringValue = config.getString(path);
            return CompressedPrivateKey.of(stringValue);
        }
        catch (final CryptoException | IllegalArgumentException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "compressed private key", "invalid-value: " + e.getMessage());
        }
    }

    /**
     * Gets the {@link Path} at the given path.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the {@link Path} value at the requested path
     * @throws ConfigException if value at path is invalid
     */
    public static Path getPath(final Config config, final String path) {
        try {
            return Paths.get(config.getString(path));
        }
        catch (final InvalidPathException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "path", "invalid: " + e.getMessage());
        }
    }

    /**
     * @throws ConfigException if value at path is invalid
     */
    public static InetAddress getInetAddress(final Config config, final String path) {
        final String stringValue = config.getString(path);
        try {
            return InetAddress.getByName(stringValue);
        }
        catch (final UnknownHostException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "inet address", "unknown-host: " + e.getMessage());
        }
    }

    /**
     * @throws ConfigException if value at path is invalid
     */
    public static List<Endpoint> getEndpointList(final Config config, final String path) {
        final List<String> stringListValue = config.getStringList(path);
        final List<Endpoint> endpointList = new ArrayList<>();
        try {
            for (final String stringValue : stringListValue) {
                endpointList.add(Endpoint.of(stringValue));
            }
        }
        catch (final IllegalArgumentException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "endpoint", "invalid-value: " + e.getMessage());
        }
        return endpointList;
    }

    /**
     * @throws ConfigException if value at path is invalid
     */
    public static Endpoint getEndpoint(final Config config, final String path) {
        final String stringValue = config.getString(path);
        try {
            return Endpoint.of(stringValue);
        }
        catch (final IllegalArgumentException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "endpoint", "invalid-value: " + e.getMessage());
        }
    }

    /**
     * Gets the short at the given path. Similar to {@link Config}, an exception is thrown for an
     * out-of-range value.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the short value at the requested path
     * @throws ConfigException.Missing   if value is absent or null
     * @throws ConfigException.WrongType if value is not convertible to a short
     */
    public static short getShort(final Config config, final String path) {
        final int integerValue = config.getInt(path);
        if (integerValue > Short.MAX_VALUE || integerValue < Short.MIN_VALUE) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "short", "out-of-range-value " + integerValue);
        }

        return (short) integerValue;
    }

    /**
     * Gets the byte at the given path. Similar to {@link Config}, an exception is thrown for an
     * out-of-range value.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the byte value at the requested path
     * @throws ConfigException.Missing   if value is absent or null
     * @throws ConfigException.WrongType if value is not convertible to a short
     */
    public static byte getByte(final Config config, final String path) {
        final int integerValue = config.getInt(path);
        if (integerValue > Byte.MAX_VALUE || integerValue < Byte.MIN_VALUE) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "byte", "out-of-range-value " + integerValue);
        }

        return (byte) integerValue;
    }

    /**
     * Gets the {@link URI} at the given path.
     *
     * @param config the application's portion of the configuration
     * @param path   path expression
     * @return the {@link URI} value at the requested path
     * @throws ConfigException if value at path is invalid
     */
    public static URI getURI(final Config config, final String path) {
        final String stringValue = config.getString(path);
        try {
            return new URI(stringValue);
        }
        catch (final NullPointerException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "uri", "null");
        }
        catch (final URISyntaxException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "inet address", "violates RFC 2396: " + e.getMessage());
        }
    }

    /**
     * @throws ConfigException if value at path is invalid
     */
    public static Set<DrasylPlugin> getPlugins(final Config config, final String path) {
        final Set<DrasylPlugin> plugins = new HashSet<>();

        for (final Map.Entry<String, ConfigValue> entry : config.getObject(path).entrySet()) {
            final String clazzName = entry.getKey();
            /*
             * Here a key is intentionally used and immediately deleted. atPath() could throw an
             * exception if the class name contains a $ character for an inner class.
             */
            final Config pluginConfig = entry.getValue().atKey("plugin").getConfig("plugin"); // NOSONAR

            if (pluginConfig.getBoolean("enabled")) {
                final DrasylPlugin plugin = initiatePlugin(path, clazzName, pluginConfig);
                plugins.add(plugin);
            }
        }
        return plugins;
    }

    @SuppressWarnings({ "java:S1192" })
    private static DrasylPlugin initiatePlugin(final String path,
                                               final String clazzName,
                                               final Config pluginConfig) {
        try {
            @SuppressWarnings("unchecked") final Class<? extends DrasylPlugin> clazz = (Class<? extends DrasylPlugin>) Class.forName(clazzName);
            final Constructor<? extends DrasylPlugin> constructor = clazz.getConstructor(Config.class);
            constructor.setAccessible(true); // NOSONAR

            return constructor.newInstance(pluginConfig);
        }
        catch (final ClassNotFoundException e) {
            throw new ConfigException.WrongType(pluginConfig.origin(), path, "plugin", "class-not-found: " + e.getMessage());
        }
        catch (final NoSuchMethodException e) {
            throw new ConfigException.WrongType(pluginConfig.origin(), path, "plugin", "no-such-method: " + e.getMessage());
        }
        catch (final IllegalAccessException e) {
            throw new ConfigException.WrongType(pluginConfig.origin(), path, "plugin", "illegal-access: " + e.getMessage());
        }
        catch (final InstantiationException e) {
            throw new ConfigException.WrongType(pluginConfig.origin(), path, "plugin", "instantiation: " + e.getMessage());
        }
        catch (final InvocationTargetException e) {
            throw new ConfigException.WrongType(pluginConfig.origin(), path, "plugin", "invocation-target: " + e.getTargetException().getMessage());
        }
    }

    /**
     * @throws ConfigException if value at path is invalid
     */
    @SuppressWarnings("unchecked")
    public static Class<ChannelInitializer<SocketChannel>> getChannelInitializer(final Config config,
                                                                                 final String path) {
        final String className = config.getString(path);
        try {
            return (Class<ChannelInitializer<SocketChannel>>) Class.forName(className);
        }
        catch (final ClassNotFoundException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "socket channel", "class-not-found: " + e.getMessage());
        }
    }

    /**
     * @throws ConfigException if value at path is invalid
     */
    public static InetSocketAddress getInetSocketAddress(final Config config, final String path) {
        final String stringValue = config.getString(path);
        try {
            final URI uriValue = new URI("my://" + stringValue);
            final String host = uriValue.getHost();
            final int port = uriValue.getPort();

            return InetSocketAddress.createUnresolved(host, port);
        }
        catch (final URISyntaxException | IllegalArgumentException e) {
            throw new ConfigException.WrongType(config.getValue(path).origin(), path, "inet socket address", e.getMessage());
        }
    }

    /**
     * Parses a file into a Config instance as with
     *
     * @param file the file to parse
     * @return the parsed configuration
     * @throws ConfigException on IO or parse errors
     */
    public static DrasylConfig parseFile(final File file) {
        return new DrasylConfig(ConfigFactory.parseFile(file).withFallback(ConfigFactory.load()));
    }

    /**
     * Parses a file into a Config instance as with
     *
     * @param s string to parse
     * @return the parsed configuration
     * @throws ConfigException on IO or parse errors
     */
    public static DrasylConfig parseString(final String s) {
        return new DrasylConfig(ConfigFactory.parseString(s).withFallback(ConfigFactory.load()));
    }

    /**
     * Creates a new builder to build a custom {@link DrasylConfig}. The built configuration is
     * derived from the default configuration. The builder must be finalized by calling {@link
     * Builder#build()} to create the resulting {@link DrasylConfig}.
     *
     * @return the new builder
     */
    public static Builder newBuilder() {
        return newBuilder(DEFAULT);
    }

    public static Builder newBuilder(final DrasylConfig config) {
        return new Builder(
                config.networkId,
                config.identityProofOfWork,
                config.identityPublicKey,
                config.identityPrivateKey,
                config.identityPath,
                config.remoteBindHost,
                config.remoteEnabled,
                config.remoteBindPort,
                config.remotePingInterval,
                config.remotePingTimeout,
                config.remotePingCommunicationTimeout,
                config.remoteUniteMinInterval,
                config.remotePingMaxPeers,
                config.remoteEndpoints,
                config.remoteExposeEnabled,
                config.remoteSuperPeerEnabled,
                config.remoteSuperPeerEndpoint,
                config.remoteMessageMtu,
                config.remoteMessageMaxContentLength,
                config.remoteMessageComposedMessageTransferTimeout,
                config.remoteMessageHopLimit,
                config.intraVmDiscoveryEnabled,
                config.localHostDiscoveryEnabled,
                config.localHostDiscoveryPath,
                config.localHostDiscoveryLeaseTime,
                config.monitoringEnabled,
                config.monitoringHostTag,
                config.monitoringInfluxUri,
                config.monitoringInfluxUser,
                config.monitoringInfluxPassword,
                config.monitoringInfluxDatabase,
                config.monitoringInfluxReportingFrequency,
                config.pluginSet,
                config.marshallingInboundAllowedTypes,
                config.marshallingInboundAllowAllPrimitives,
                config.marshallingInboundAllowArrayOfDefinedTypes,
                config.marshallingInboundAllowedPackages,
                config.marshallingOutboundAllowedTypes,
                config.marshallingOutboundAllowAllPrimitives,
                config.marshallingOutboundAllowArrayOfDefinedTypes,
                config.marshallingOutboundAllowedPackages
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(networkId, identityProofOfWork, identityPublicKey, identityPrivateKey, identityPath, remoteBindHost, remoteEnabled, remoteBindPort, remotePingInterval, remotePingTimeout, remotePingCommunicationTimeout, remoteUniteMinInterval, remotePingMaxPeers, remoteEndpoints, remoteExposeEnabled, remoteMessageMtu, remoteMessageMaxContentLength, remoteMessageComposedMessageTransferTimeout, remoteMessageHopLimit, remoteSuperPeerEnabled, remoteSuperPeerEndpoint, intraVmDiscoveryEnabled, localHostDiscoveryEnabled, localHostDiscoveryPath, localHostDiscoveryLeaseTime, monitoringEnabled, monitoringHostTag, monitoringInfluxUri, monitoringInfluxUser, monitoringInfluxPassword, monitoringInfluxDatabase, monitoringInfluxReportingFrequency, pluginSet, marshallingInboundAllowedTypes, marshallingInboundAllowAllPrimitives, marshallingInboundAllowArrayOfDefinedTypes, marshallingInboundAllowedPackages, marshallingOutboundAllowedTypes, marshallingOutboundAllowAllPrimitives, marshallingOutboundAllowArrayOfDefinedTypes, marshallingOutboundAllowedPackages);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DrasylConfig that = (DrasylConfig) o;
        return networkId == that.networkId &&
                remoteEnabled == that.remoteEnabled &&
                remoteBindPort == that.remoteBindPort &&
                remotePingMaxPeers == that.remotePingMaxPeers &&
                remoteExposeEnabled == that.remoteExposeEnabled &&
                remoteMessageMtu == that.remoteMessageMtu &&
                remoteMessageMaxContentLength == that.remoteMessageMaxContentLength &&
                remoteMessageHopLimit == that.remoteMessageHopLimit &&
                remoteSuperPeerEnabled == that.remoteSuperPeerEnabled &&
                intraVmDiscoveryEnabled == that.intraVmDiscoveryEnabled &&
                localHostDiscoveryEnabled == that.localHostDiscoveryEnabled &&
                monitoringEnabled == that.monitoringEnabled &&
                marshallingInboundAllowAllPrimitives == that.marshallingInboundAllowAllPrimitives &&
                marshallingInboundAllowArrayOfDefinedTypes == that.marshallingInboundAllowArrayOfDefinedTypes &&
                marshallingOutboundAllowAllPrimitives == that.marshallingOutboundAllowAllPrimitives &&
                marshallingOutboundAllowArrayOfDefinedTypes == that.marshallingOutboundAllowArrayOfDefinedTypes &&
                Objects.equals(identityProofOfWork, that.identityProofOfWork) &&
                Objects.equals(identityPublicKey, that.identityPublicKey) &&
                Objects.equals(identityPrivateKey, that.identityPrivateKey) &&
                Objects.equals(identityPath, that.identityPath) &&
                Objects.equals(remoteBindHost, that.remoteBindHost) &&
                Objects.equals(remotePingInterval, that.remotePingInterval) &&
                Objects.equals(remotePingTimeout, that.remotePingTimeout) &&
                Objects.equals(remotePingCommunicationTimeout, that.remotePingCommunicationTimeout) &&
                Objects.equals(remoteUniteMinInterval, that.remoteUniteMinInterval) &&
                Objects.equals(remoteEndpoints, that.remoteEndpoints) &&
                Objects.equals(remoteMessageComposedMessageTransferTimeout, that.remoteMessageComposedMessageTransferTimeout) &&
                Objects.equals(remoteSuperPeerEndpoint, that.remoteSuperPeerEndpoint) &&
                Objects.equals(localHostDiscoveryPath, that.localHostDiscoveryPath) &&
                Objects.equals(localHostDiscoveryLeaseTime, that.localHostDiscoveryLeaseTime) &&
                Objects.equals(monitoringHostTag, that.monitoringHostTag) &&
                Objects.equals(monitoringInfluxUri, that.monitoringInfluxUri) &&
                Objects.equals(monitoringInfluxUser, that.monitoringInfluxUser) &&
                Objects.equals(monitoringInfluxPassword, that.monitoringInfluxPassword) &&
                Objects.equals(monitoringInfluxDatabase, that.monitoringInfluxDatabase) &&
                Objects.equals(monitoringInfluxReportingFrequency, that.monitoringInfluxReportingFrequency) &&
                Objects.equals(pluginSet, that.pluginSet) &&
                Objects.equals(marshallingInboundAllowedTypes, that.marshallingInboundAllowedTypes) &&
                Objects.equals(marshallingInboundAllowedPackages, that.marshallingInboundAllowedPackages) &&
                Objects.equals(marshallingOutboundAllowedTypes, that.marshallingOutboundAllowedTypes) &&
                Objects.equals(marshallingOutboundAllowedPackages, that.marshallingOutboundAllowedPackages);
    }

    @Override
    public String toString() {
        return "DrasylConfig{" +
                "networkId=" + networkId +
                ", identityProofOfWork=" + identityProofOfWork +
                ", identityPublicKey=" + identityPublicKey +
                ", identityPrivateKey=" + maskSecret(identityPrivateKey) +
                ", identityPath=" + identityPath +
                ", remoteBindHost=" + remoteBindHost +
                ", remoteEnabled=" + remoteEnabled +
                ", remoteBindPort=" + remoteBindPort +
                ", remotePingInterval=" + remotePingInterval +
                ", remotePingTimeout=" + remotePingTimeout +
                ", remotePingCommunicationTimeout=" + remotePingCommunicationTimeout +
                ", remoteUniteMinInterval=" + remoteUniteMinInterval +
                ", remotePingMaxPeers=" + remotePingMaxPeers +
                ", remoteEndpoints=" + remoteEndpoints +
                ", remoteExposeEnabled=" + remoteExposeEnabled +
                ", remoteMessageMtu=" + remoteMessageMtu +
                ", remoteMessageMaxContentLength=" + remoteMessageMaxContentLength +
                ", remoteMessageComposedMessageTransferTimeout=" + remoteMessageComposedMessageTransferTimeout +
                ", remoteMessageHopLimit=" + remoteMessageHopLimit +
                ", remoteSuperPeerEnabled=" + remoteSuperPeerEnabled +
                ", remoteSuperPeerEndpoint=" + remoteSuperPeerEndpoint +
                ", intraVmDiscoveryEnabled=" + intraVmDiscoveryEnabled +
                ", localHostDiscoveryEnabled=" + localHostDiscoveryEnabled +
                ", localHostDiscoveryPath=" + localHostDiscoveryPath +
                ", localHostDiscoveryLeaseTime=" + localHostDiscoveryLeaseTime +
                ", monitoringEnabled=" + monitoringEnabled +
                ", monitoringHostTag=" + monitoringHostTag +
                ", monitoringInfluxUri=" + monitoringInfluxUri +
                ", monitoringInfluxUser='" + monitoringInfluxUser + '\'' +
                ", monitoringInfluxPassword='" + maskSecret(monitoringInfluxPassword) + '\'' +
                ", monitoringInfluxDatabase='" + monitoringInfluxDatabase + '\'' +
                ", monitoringInfluxReportingFrequency=" + monitoringInfluxReportingFrequency +
                ", pluginSet=" + pluginSet +
                ", marshallingInboundAllowedTypes=" + marshallingInboundAllowedTypes +
                ", marshallingInboundAllowAllPrimitives=" + marshallingInboundAllowAllPrimitives +
                ", marshallingInboundAllowArrayOfDefinedTypes=" + marshallingInboundAllowArrayOfDefinedTypes +
                ", marshallingInboundAllowedPackages=" + marshallingInboundAllowedPackages +
                ", marshallingOutboundAllowedTypes=" + marshallingOutboundAllowedTypes +
                ", marshallingOutboundAllowAllPrimitives=" + marshallingOutboundAllowAllPrimitives +
                ", marshallingOutboundAllowArrayOfDefinedTypes=" + marshallingOutboundAllowArrayOfDefinedTypes +
                ", marshallingOutboundAllowedPackages=" + marshallingOutboundAllowedPackages +
                '}';
    }

    public Duration getRemotePingTimeout() {
        return remotePingTimeout;
    }

    public Duration getRemotePingCommunicationTimeout() {
        return remotePingCommunicationTimeout;
    }

    public Duration getRemoteUniteMinInterval() {
        return remoteUniteMinInterval;
    }

    public int getRemotePingMaxPeers() {
        return remotePingMaxPeers;
    }

    public int getNetworkId() {
        return networkId;
    }

    public boolean isMonitoringEnabled() {
        return monitoringEnabled;
    }

    public String getMonitoringHostTag() {
        return monitoringHostTag;
    }

    public URI getMonitoringInfluxUri() {
        return monitoringInfluxUri;
    }

    public String getMonitoringInfluxUser() {
        return monitoringInfluxUser;
    }

    public String getMonitoringInfluxPassword() {
        return monitoringInfluxPassword;
    }

    public String getMonitoringInfluxDatabase() {
        return monitoringInfluxDatabase;
    }

    public Duration getMonitoringInfluxReportingFrequency() {
        return monitoringInfluxReportingFrequency;
    }

    public boolean isRemoteEnabled() {
        return remoteEnabled;
    }

    public InetAddress getRemoteBindHost() {
        return remoteBindHost;
    }

    public int getRemoteBindPort() {
        return remoteBindPort;
    }

    public Set<Endpoint> getRemoteEndpoints() {
        return remoteEndpoints;
    }

    public boolean isRemoteExposeEnabled() {
        return remoteExposeEnabled;
    }

    public Duration getRemotePingInterval() {
        return remotePingInterval;
    }

    public boolean isRemoteSuperPeerEnabled() {
        return remoteSuperPeerEnabled;
    }

    public Endpoint getRemoteSuperPeerEndpoint() {
        return remoteSuperPeerEndpoint;
    }

    public ProofOfWork getIdentityProofOfWork() {
        return identityProofOfWork;
    }

    public CompressedPublicKey getIdentityPublicKey() {
        return identityPublicKey;
    }

    public CompressedPrivateKey getIdentityPrivateKey() {
        return identityPrivateKey;
    }

    public Path getIdentityPath() {
        return identityPath;
    }

    public int getRemoteMessageMtu() {
        return remoteMessageMtu;
    }

    public int getRemoteMessageMaxContentLength() {
        return remoteMessageMaxContentLength;
    }

    public Duration getRemoteMessageComposedMessageTransferTimeout() {
        return remoteMessageComposedMessageTransferTimeout;
    }

    public byte getRemoteMessageHopLimit() {
        return remoteMessageHopLimit;
    }

    public boolean isIntraVmDiscoveryEnabled() {
        return intraVmDiscoveryEnabled;
    }

    public boolean isLocalHostDiscoveryEnabled() {
        return localHostDiscoveryEnabled;
    }

    public Path getLocalHostDiscoveryPath() {
        return localHostDiscoveryPath;
    }

    public Duration getLocalHostDiscoveryLeaseTime() {
        return localHostDiscoveryLeaseTime;
    }

    public Set<DrasylPlugin> getPlugins() {
        return pluginSet;
    }

    public List<String> getMarshallingInboundAllowedTypes() {
        return marshallingInboundAllowedTypes;
    }

    public boolean isMarshallingInboundAllowAllPrimitives() {
        return marshallingInboundAllowAllPrimitives;
    }

    public boolean isMarshallingInboundAllowArrayOfDefinedTypes() {
        return marshallingInboundAllowArrayOfDefinedTypes;
    }

    public List<String> getMarshallingInboundAllowedPackages() {
        return marshallingInboundAllowedPackages;
    }

    public List<String> getMarshallingOutboundAllowedTypes() {
        return marshallingOutboundAllowedTypes;
    }

    public boolean isMarshallingOutboundAllowAllPrimitives() {
        return marshallingOutboundAllowAllPrimitives;
    }

    public boolean isMarshallingOutboundAllowArrayOfDefinedTypes() {
        return marshallingOutboundAllowArrayOfDefinedTypes;
    }

    public List<String> getMarshallingOutboundAllowedPackages() {
        return marshallingOutboundAllowedPackages;
    }

    public static final class Builder {
        //======================================= Config Values ========================================
        private int networkId;
        private ProofOfWork identityProofOfWork;
        private CompressedPublicKey identityPublicKey;
        private CompressedPrivateKey identityPrivateKey;
        private Path identityPath;
        private InetAddress remoteBindHost;
        private boolean remoteEnabled;
        private int remoteBindPort;
        private Duration remotePingInterval;
        private Duration remotePingTimeout;
        private Duration remotePingCommunicationTimeout;
        private Duration remoteUniteMinInterval;
        private int remotePingMaxPeers;
        private Set<Endpoint> remoteEndpoints;
        private boolean remoteExposeEnabled;
        private int remoteMessageMtu;
        private int remoteMessageMaxContentLength;
        private byte remoteMessageHopLimit;
        private Duration remoteMessageComposedMessageTransferTimeout;
        private boolean remoteSuperPeerEnabled;
        private Endpoint remoteSuperPeerEndpoint;
        private boolean intraVmDiscoveryEnabled;
        private boolean localHostDiscoveryEnabled;
        private Path localHostDiscoveryPath;
        private Duration localHostDiscoveryLeaseTime;
        private boolean monitoringEnabled;
        private String monitoringHost;
        private URI monitoringInfluxUri;
        private String monitoringInfluxUser;
        private String monitoringInfluxPassword;
        private String monitoringInfluxDatabase;
        private Duration monitoringInfluxReportingFrequency;
        private Set<DrasylPlugin> pluginSet;
        private List<String> marshallingInboundAllowedTypes;
        private boolean marshallingInboundAllowAllPrimitives;
        private boolean marshallingInboundAllowArrayOfDefinedTypes;
        private List<String> marshallingInboundAllowedPackages;
        private List<String> marshallingOutboundAllowedTypes;
        private boolean marshallingOutboundAllowAllPrimitives;
        private boolean marshallingOutboundAllowArrayOfDefinedTypes;
        private List<String> marshallingOutboundAllowedPackages;

        private Builder() {
        }

        @SuppressWarnings({ "java:S107" })
        public Builder(final int networkId,
                       final ProofOfWork identityProofOfWork,
                       final CompressedPublicKey identityPublicKey,
                       final CompressedPrivateKey identityPrivateKey,
                       final Path identityPath,
                       final InetAddress remoteBindHost,
                       final boolean remoteEnabled,
                       final int remoteBindPort,
                       final Duration remotePingInterval,
                       final Duration remotePingTimeout,
                       final Duration remotePingCommunicationTimeout,
                       final Duration remoteUniteMinInterval,
                       final int remotePingMaxPeers,
                       final Set<Endpoint> remoteEndpoints,
                       final boolean remoteExposeEnabled,
                       final boolean remoteSuperPeerEnabled,
                       final Endpoint remoteSuperPeerEndpoint,
                       final int remoteMessageMtu,
                       final int remoteMessageMaxContentLength,
                       final Duration remoteMessageComposedMessageTransferTimeout,
                       final byte remoteMessageHopLimit,
                       final boolean intraVmDiscoveryEnabled,
                       final boolean localHostDiscoveryEnabled,
                       final Path localHostDiscoveryPath,
                       final Duration localHostDiscoveryLeaseTime,
                       final boolean monitoringEnabled,
                       final String monitoringHost,
                       final URI monitoringInfluxUri,
                       final String monitoringInfluxUser,
                       final String monitoringInfluxPassword,
                       final String monitoringInfluxDatabase,
                       final Duration monitoringInfluxReportingFrequency,
                       final Set<DrasylPlugin> pluginSet,
                       final List<String> marshallingInboundAllowedTypes,
                       final boolean marshallingInboundAllowAllPrimitives,
                       final boolean marshallingInboundAllowArrayOfDefinedTypes,
                       final List<String> marshallingInboundAllowedPackages,
                       final List<String> marshallingOutboundAllowedTypes,
                       final boolean marshallingOutboundAllowAllPrimitives,
                       final boolean marshallingOutboundAllowArrayOfDefinedTypes,
                       final List<String> marshallingOutboundAllowedPackages) {
            this.networkId = networkId;
            this.identityProofOfWork = identityProofOfWork;
            this.identityPublicKey = identityPublicKey;
            this.identityPrivateKey = identityPrivateKey;
            this.identityPath = identityPath;
            this.remoteBindHost = remoteBindHost;
            this.remoteEnabled = remoteEnabled;
            this.monitoringHost = monitoringHost;
            this.remoteBindPort = remoteBindPort;
            this.remotePingInterval = remotePingInterval;
            this.remotePingTimeout = remotePingTimeout;
            this.remotePingCommunicationTimeout = remotePingCommunicationTimeout;
            this.remoteUniteMinInterval = remoteUniteMinInterval;
            this.remotePingMaxPeers = remotePingMaxPeers;
            this.remoteEndpoints = remoteEndpoints;
            this.remoteExposeEnabled = remoteExposeEnabled;
            this.remoteMessageMtu = remoteMessageMtu;
            this.remoteMessageMaxContentLength = remoteMessageMaxContentLength;
            this.remoteMessageHopLimit = remoteMessageHopLimit;
            this.remoteMessageComposedMessageTransferTimeout = remoteMessageComposedMessageTransferTimeout;
            this.remoteSuperPeerEnabled = remoteSuperPeerEnabled;
            this.remoteSuperPeerEndpoint = remoteSuperPeerEndpoint;
            this.intraVmDiscoveryEnabled = intraVmDiscoveryEnabled;
            this.localHostDiscoveryEnabled = localHostDiscoveryEnabled;
            this.localHostDiscoveryPath = localHostDiscoveryPath;
            this.localHostDiscoveryLeaseTime = localHostDiscoveryLeaseTime;
            this.monitoringEnabled = monitoringEnabled;
            this.monitoringInfluxUri = monitoringInfluxUri;
            this.monitoringInfluxUser = monitoringInfluxUser;
            this.monitoringInfluxPassword = monitoringInfluxPassword;
            this.monitoringInfluxDatabase = monitoringInfluxDatabase;
            this.monitoringInfluxReportingFrequency = monitoringInfluxReportingFrequency;
            this.pluginSet = pluginSet;
            this.marshallingInboundAllowedTypes = marshallingInboundAllowedTypes;
            this.marshallingInboundAllowAllPrimitives = marshallingInboundAllowAllPrimitives;
            this.marshallingInboundAllowArrayOfDefinedTypes = marshallingInboundAllowArrayOfDefinedTypes;
            this.marshallingInboundAllowedPackages = marshallingInboundAllowedPackages;
            this.marshallingOutboundAllowedTypes = marshallingOutboundAllowedTypes;
            this.marshallingOutboundAllowAllPrimitives = marshallingOutboundAllowAllPrimitives;
            this.marshallingOutboundAllowArrayOfDefinedTypes = marshallingOutboundAllowArrayOfDefinedTypes;
            this.marshallingOutboundAllowedPackages = marshallingOutboundAllowedPackages;
        }

        public Builder networkId(final int networkId) {
            this.networkId = networkId;
            return this;
        }

        public Builder identityProofOfWork(final ProofOfWork identityProofOfWork) {
            this.identityProofOfWork = identityProofOfWork;
            return this;
        }

        public Builder identityPublicKey(final CompressedPublicKey identityPublicKey) {
            this.identityPublicKey = identityPublicKey;
            return this;
        }

        public Builder identityPrivateKey(final CompressedPrivateKey identityPrivateKey) {
            this.identityPrivateKey = identityPrivateKey;
            return this;
        }

        public Builder identityPath(final Path identityPath) {
            this.identityPath = identityPath;
            return this;
        }

        public Builder remoteBindHost(final InetAddress remoteBindHost) {
            this.remoteBindHost = remoteBindHost;
            return this;
        }

        public Builder remoteEnabled(final boolean remoteEnabled) {
            this.remoteEnabled = remoteEnabled;
            return this;
        }

        public Builder remoteBindPort(final int remoteBindPort) {
            this.remoteBindPort = remoteBindPort;
            return this;
        }

        public Builder remotePingInterval(final Duration remotePingInterval) {
            this.remotePingInterval = remotePingInterval;
            return this;
        }

        public Builder remotePingTimeout(final Duration remotePingTimeout) {
            this.remotePingTimeout = remotePingTimeout;
            return this;
        }

        public Builder remotePingCommunicationTimeout(final Duration remotePingCommunicationTimeout) {
            this.remotePingCommunicationTimeout = remotePingCommunicationTimeout;
            return this;
        }

        public Builder remoteUniteMinInterval(final Duration remoteUniteMinInterval) {
            this.remoteUniteMinInterval = remoteUniteMinInterval;
            return this;
        }

        public Builder remotePingMaxPeers(final int remotePingMaxPeers) {
            this.remotePingMaxPeers = remotePingMaxPeers;
            return this;
        }

        public Builder remoteEndpoints(final Set<Endpoint> remoteEndpoints) {
            this.remoteEndpoints = remoteEndpoints;
            return this;
        }

        public Builder remoteExposeEnabled(final boolean remoteExposeEnabled) {
            this.remoteExposeEnabled = remoteExposeEnabled;
            return this;
        }

        public Builder remoteMessageMtu(final int remoteMessageMtu) {
            this.remoteMessageMtu = remoteMessageMtu;
            return this;
        }

        public Builder remoteMessageMaxContentLength(final int remoteMessageMaxContentLength) {
            this.remoteMessageMaxContentLength = remoteMessageMaxContentLength;
            return this;
        }

        public Builder remoteMessageHopLimit(final byte remoteMessageHopLimit) {
            this.remoteMessageHopLimit = remoteMessageHopLimit;
            return this;
        }

        public Builder remoteMessageComposedMessageTransferTimeout(final Duration messageComposedMessageTransferTimeout) {
            this.remoteMessageComposedMessageTransferTimeout = messageComposedMessageTransferTimeout;
            return this;
        }

        public Builder remoteSuperPeerEnabled(final boolean remoteSuperPeerEnabled) {
            this.remoteSuperPeerEnabled = remoteSuperPeerEnabled;
            return this;
        }

        public Builder remoteSuperPeerEndpoint(final Endpoint remoteSuperPeerEndpoint) {
            this.remoteSuperPeerEndpoint = remoteSuperPeerEndpoint;
            return this;
        }

        public Builder intraVmDiscoveryEnabled(final boolean intraVmDiscoveryEnabled) {
            this.intraVmDiscoveryEnabled = intraVmDiscoveryEnabled;
            return this;
        }

        public Builder localHostDiscoveryEnabled(final boolean localHostDiscoveryEnabled) {
            this.localHostDiscoveryEnabled = localHostDiscoveryEnabled;
            return this;
        }

        public Builder localHostDiscoveryPath(final Path localHostDiscoveryPath) {
            this.localHostDiscoveryPath = localHostDiscoveryPath;
            return this;
        }

        public Builder localHostDiscoveryLeaseTime(final Duration localHostDiscoveryLeaseTime) {
            this.localHostDiscoveryLeaseTime = localHostDiscoveryLeaseTime;
            return this;
        }

        public Builder monitoringEnabled(final boolean monitoringEnabled) {
            this.monitoringEnabled = monitoringEnabled;
            return this;
        }

        public Builder monitoringHost(final String monitoringHost) {
            this.monitoringHost = monitoringHost;
            return this;
        }

        public Builder monitoringInfluxUri(final URI monitoringInfluxUri) {
            this.monitoringInfluxUri = monitoringInfluxUri;
            return this;
        }

        public Builder monitoringInfluxUser(final String monitoringInfluxUser) {
            this.monitoringInfluxUser = monitoringInfluxUser;
            return this;
        }

        public Builder monitoringInfluxPassword(final String monitoringInfluxPassword) {
            this.monitoringInfluxPassword = monitoringInfluxPassword;
            return this;
        }

        public Builder monitoringInfluxDatabase(final String monitoringInfluxDatabase) {
            this.monitoringInfluxDatabase = monitoringInfluxDatabase;
            return this;
        }

        public Builder monitoringInfluxReportingFrequency(final Duration monitoringInfluxReportingFrequency) {
            this.monitoringInfluxReportingFrequency = monitoringInfluxReportingFrequency;
            return this;
        }

        public Builder plugins(final Set<DrasylPlugin> pluginSet) {
            this.pluginSet = pluginSet;
            return this;
        }

        public Builder marshallingInboundAllowedTypes(final List<String> marshallingInboundAllowedTypes) {
            this.marshallingInboundAllowedTypes = marshallingInboundAllowedTypes;
            return this;
        }

        public Builder marshallingInboundAllowAllPrimitives(final boolean marshallingInboundAllowAllPrimitives) {
            this.marshallingInboundAllowAllPrimitives = marshallingInboundAllowAllPrimitives;
            return this;
        }

        public Builder marshallingInboundAllowArrayOfDefinedTypes(final boolean marshallingInboundAllowArrayOfDefinedTypes) {
            this.marshallingInboundAllowArrayOfDefinedTypes = marshallingInboundAllowArrayOfDefinedTypes;
            return this;
        }

        public Builder marshallingInboundAllowedPackages(final List<String> marshallingInboundAllowedPackages) {
            this.marshallingInboundAllowedPackages = marshallingInboundAllowedPackages;
            return this;
        }

        public Builder marshallingOutboundAllowedTypes(final List<String> marshallingOutboundAllowedTypes) {
            this.marshallingOutboundAllowedTypes = marshallingOutboundAllowedTypes;
            return this;
        }

        public Builder marshallingOutboundAllowAllPrimitives(final boolean marshallingOutboundAllowAllPrimitives) {
            this.marshallingOutboundAllowAllPrimitives = marshallingOutboundAllowAllPrimitives;
            return this;
        }

        public Builder marshallingOutboundAllowArrayOfDefinedTypes(final boolean marshallingOutboundAllowArrayOfDefinedTypes) {
            this.marshallingOutboundAllowArrayOfDefinedTypes = marshallingOutboundAllowArrayOfDefinedTypes;
            return this;
        }

        public Builder marshallingOutboundAllowedPackages(final List<String> marshallingOutboundAllowedPackages) {
            this.marshallingOutboundAllowedPackages = marshallingOutboundAllowedPackages;
            return this;
        }

        public DrasylConfig build() {
            return new DrasylConfig(networkId, identityProofOfWork, identityPublicKey, identityPrivateKey, identityPath, remoteBindHost, remoteEnabled, remoteBindPort, remotePingInterval, remotePingTimeout, remotePingCommunicationTimeout, remoteUniteMinInterval, remotePingMaxPeers, remoteEndpoints, remoteExposeEnabled, remoteSuperPeerEnabled, remoteSuperPeerEndpoint, remoteMessageMaxContentLength, remoteMessageHopLimit, remoteMessageComposedMessageTransferTimeout, remoteMessageMtu, intraVmDiscoveryEnabled, localHostDiscoveryEnabled, localHostDiscoveryPath, localHostDiscoveryLeaseTime, monitoringEnabled, monitoringHost, monitoringInfluxUri, monitoringInfluxUser, monitoringInfluxPassword, monitoringInfluxDatabase, monitoringInfluxReportingFrequency, pluginSet, marshallingInboundAllowedTypes, marshallingInboundAllowAllPrimitives, marshallingInboundAllowArrayOfDefinedTypes, marshallingInboundAllowedPackages, marshallingOutboundAllowedTypes, marshallingOutboundAllowAllPrimitives, marshallingOutboundAllowArrayOfDefinedTypes, marshallingOutboundAllowedPackages);
        }
    }
}