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
import com.typesafe.config.ConfigMemorySize;
import com.typesafe.config.ConfigObject;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPrivateKey;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.Endpoint;
import org.drasyl.plugin.DrasylPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static java.time.Duration.ofSeconds;
import static org.drasyl.DrasylConfig.DEFAULT;
import static org.drasyl.DrasylConfig.IDENTITY_PATH;
import static org.drasyl.DrasylConfig.IDENTITY_PRIVATE_KEY;
import static org.drasyl.DrasylConfig.IDENTITY_PROOF_OF_WORK;
import static org.drasyl.DrasylConfig.IDENTITY_PUBLIC_KEY;
import static org.drasyl.DrasylConfig.INTRA_VM_DISCOVERY_ENABLED;
import static org.drasyl.DrasylConfig.LOCAL_HOST_DISCOVERY_ENABLED;
import static org.drasyl.DrasylConfig.LOCAL_HOST_DISCOVERY_LEASE_TIME;
import static org.drasyl.DrasylConfig.LOCAL_HOST_DISCOVERY_PATH;
import static org.drasyl.DrasylConfig.MARSHALLING_INBOUND_ALLOWED_PACKAGES;
import static org.drasyl.DrasylConfig.MARSHALLING_INBOUND_ALLOWED_TYPES;
import static org.drasyl.DrasylConfig.MARSHALLING_INBOUND_ALLOW_ALL_PRIMITIVES;
import static org.drasyl.DrasylConfig.MARSHALLING_INBOUND_ALLOW_ARRAY_OF_DEFINED_TYPES;
import static org.drasyl.DrasylConfig.MARSHALLING_OUTBOUND_ALLOWED_PACKAGES;
import static org.drasyl.DrasylConfig.MARSHALLING_OUTBOUND_ALLOWED_TYPES;
import static org.drasyl.DrasylConfig.MARSHALLING_OUTBOUND_ALLOW_ALL_PRIMITIVES;
import static org.drasyl.DrasylConfig.MARSHALLING_OUTBOUND_ALLOW_ARRAY_OF_DEFINED_TYPES;
import static org.drasyl.DrasylConfig.MONITORING_ENABLED;
import static org.drasyl.DrasylConfig.MONITORING_HOST_TAG;
import static org.drasyl.DrasylConfig.MONITORING_INFLUX_DATABASE;
import static org.drasyl.DrasylConfig.MONITORING_INFLUX_PASSWORD;
import static org.drasyl.DrasylConfig.MONITORING_INFLUX_REPORTING_FREQUENCY;
import static org.drasyl.DrasylConfig.MONITORING_INFLUX_URI;
import static org.drasyl.DrasylConfig.MONITORING_INFLUX_USER;
import static org.drasyl.DrasylConfig.NETWORK_ID;
import static org.drasyl.DrasylConfig.PLUGINS;
import static org.drasyl.DrasylConfig.REMOTE_BIND_HOST;
import static org.drasyl.DrasylConfig.REMOTE_BIND_PORT;
import static org.drasyl.DrasylConfig.REMOTE_ENABLED;
import static org.drasyl.DrasylConfig.REMOTE_ENDPOINTS;
import static org.drasyl.DrasylConfig.REMOTE_EXPOSE_ENABLED;
import static org.drasyl.DrasylConfig.REMOTE_MESSAGE_COMPOSED_MESSAGE_TRANSFER_TIMEOUT;
import static org.drasyl.DrasylConfig.REMOTE_MESSAGE_HOP_LIMIT;
import static org.drasyl.DrasylConfig.REMOTE_MESSAGE_MAX_CONTENT_LENGTH;
import static org.drasyl.DrasylConfig.REMOTE_MESSAGE_MTU;
import static org.drasyl.DrasylConfig.REMOTE_PING_INTERVAL;
import static org.drasyl.DrasylConfig.REMOTE_SUPER_PEER_ENABLED;
import static org.drasyl.DrasylConfig.REMOTE_SUPER_PEER_ENDPOINT;
import static org.drasyl.DrasylConfig.getByte;
import static org.drasyl.DrasylConfig.getChannelInitializer;
import static org.drasyl.DrasylConfig.getEndpointList;
import static org.drasyl.DrasylConfig.getInetAddress;
import static org.drasyl.DrasylConfig.getInetSocketAddress;
import static org.drasyl.DrasylConfig.getPath;
import static org.drasyl.DrasylConfig.getPlugins;
import static org.drasyl.DrasylConfig.getPrivateKey;
import static org.drasyl.DrasylConfig.getPublicKey;
import static org.drasyl.DrasylConfig.getURI;
import static org.drasyl.util.NetworkUtil.createInetAddress;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
class DrasylConfigTest {
    private int networkId;
    @Mock
    private CompressedPublicKey identityPublicKey;
    @Mock
    private ProofOfWork identityProofOfWork;
    @Mock
    private CompressedPrivateKey identityPrivateKey;
    @Mock
    private Path identityPath;
    private InetAddress serverBindHost;
    private boolean serverEnabled;
    private int serverBindPort;
    private Duration serverHandshakeTimeout;
    private Set<Endpoint> serverEndpoints;
    private boolean remoteExposeEnabled;
    private int remoteMessageMtu;
    private int remoteMessageMaxContentLength;
    private byte remoteMessageHopLimit;
    private boolean superPeerEnabled;
    private Endpoint superPeerEndpoint;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Config typesafeConfig;
    private String identityPathAsString;
    private boolean intraVmDiscoveryEnabled;
    private boolean localHostDiscoveryEnabled;
    private String localHostDiscoveryPathAsString;
    private Duration localHostDiscoveryLeaseTime;
    private Duration composedMessageTransferTimeout;
    private boolean monitoringEnabled;
    private String monitoringHostTag;
    private URI monitoringInfluxUri;
    private String monitoringInfluxUser;
    private String monitoringInfluxPassword;
    private String monitoringInfluxDatabase;
    private Duration monitoringInfluxReportingFrequency;
    private Set<DrasylPlugin> plugins;
    private List<String> marshallingInboundAllowedTypes;
    private boolean marshallingInboundAllowAllPrimitives;
    private boolean marshallingInboundAllowArrayOfDefinedTypes;
    private List<String> marshallingInboundAllowedPackages;
    private List<String> marshallingOutboundAllowedTypes;
    private boolean marshallingOutboundAllowAllPrimitives;
    private boolean marshallingOutboundAllowArrayOfDefinedTypes;
    private List<String> marshallingOutboundAllowedPackages;
    private Duration remotePingTimeout;
    private Duration remotePingCommunicationTimeout;
    private Duration remoteUniteMinInterval;
    private int remotePingMaxPeers;

    @BeforeEach
    void setUp() {
        networkId = 1337;
        serverBindHost = createInetAddress("0.0.0.0");
        serverEnabled = true;
        serverBindPort = 22527;
        serverHandshakeTimeout = ofSeconds(30);
        serverEndpoints = Set.of();
        remoteExposeEnabled = true;
        remoteMessageMaxContentLength = 1024;
        remoteMessageHopLimit = (byte) 64;
        superPeerEnabled = true;
        superPeerEndpoint = Endpoint.of("udp://foo.bar:123#030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
        identityPathAsString = "drasyl.identity.json";
        intraVmDiscoveryEnabled = true;
        localHostDiscoveryEnabled = true;
        localHostDiscoveryPathAsString = "foo/bar";
        localHostDiscoveryLeaseTime = ofSeconds(60);
        composedMessageTransferTimeout = ofSeconds(60);
        monitoringEnabled = true;
        monitoringHostTag = "test.example.com";
        monitoringInfluxUri = URI.create("http://localhost:8086");
        monitoringInfluxUser = "";
        monitoringInfluxPassword = "";
        monitoringInfluxDatabase = "drasyl";
        monitoringInfluxReportingFrequency = ofSeconds(60);
        plugins = Set.of();
        marshallingInboundAllowedTypes = List.of();
        marshallingInboundAllowAllPrimitives = true;
        marshallingInboundAllowArrayOfDefinedTypes = true;
        marshallingInboundAllowedPackages = List.of();
        marshallingOutboundAllowedTypes = List.of();
        marshallingOutboundAllowAllPrimitives = true;
        marshallingOutboundAllowArrayOfDefinedTypes = true;
        marshallingOutboundAllowedPackages = List.of();
    }

    static class MyPlugin implements DrasylPlugin {
        // do not alter constructor signature. We need that for testing
        public MyPlugin(final Config config) {
        }
    }

    static class MyPluginWithMissingMethod implements DrasylPlugin {
    }

    static class MyPluginWithInvocationTargetException implements DrasylPlugin {
        public MyPluginWithInvocationTargetException(final Config config) throws IllegalAccessException {
            throw new IllegalAccessException("boom");
        }
    }

    @Nested
    class Constructor {
        @Test
        @SuppressWarnings("java:S5961")
        void shouldReadConfigProperly() throws CryptoException {
            when(typesafeConfig.getInt(NETWORK_ID)).thenReturn(networkId);
            when(typesafeConfig.getInt(IDENTITY_PROOF_OF_WORK)).thenReturn(-1);
            when(typesafeConfig.getString(IDENTITY_PUBLIC_KEY)).thenReturn("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3");
            when(typesafeConfig.getString(IDENTITY_PRIVATE_KEY)).thenReturn("0b01459ef93b2b7dc22794a3b9b7e8fac293399cf9add5b2375d9c357a64546d");
            when(typesafeConfig.getInt(IDENTITY_PROOF_OF_WORK)).thenReturn(123);
            when(typesafeConfig.getString(IDENTITY_PATH)).thenReturn(identityPathAsString);
            when(typesafeConfig.getBoolean(REMOTE_ENABLED)).thenReturn(serverEnabled);
            when(typesafeConfig.getString(REMOTE_BIND_HOST)).thenReturn(serverBindHost.getHostAddress());
            when(typesafeConfig.getInt(REMOTE_BIND_PORT)).thenReturn(serverBindPort);
            when(typesafeConfig.getDuration(REMOTE_PING_INTERVAL)).thenReturn(serverHandshakeTimeout);
            when(typesafeConfig.getMemorySize(REMOTE_MESSAGE_MTU)).thenReturn(ConfigMemorySize.ofBytes(remoteMessageMtu));
            when(typesafeConfig.getMemorySize(REMOTE_MESSAGE_MAX_CONTENT_LENGTH)).thenReturn(ConfigMemorySize.ofBytes(remoteMessageMaxContentLength));
            when(typesafeConfig.getInt(REMOTE_MESSAGE_HOP_LIMIT)).thenReturn((int) remoteMessageHopLimit);
            when(typesafeConfig.getStringList(REMOTE_ENDPOINTS)).thenReturn(List.of());
            when(typesafeConfig.getBoolean(REMOTE_EXPOSE_ENABLED)).thenReturn(remoteExposeEnabled);
            when(typesafeConfig.getBoolean(REMOTE_SUPER_PEER_ENABLED)).thenReturn(superPeerEnabled);
            when(typesafeConfig.getString(REMOTE_SUPER_PEER_ENDPOINT)).thenReturn("udp://foo.bar:123#030e54504c1b64d9e31d5cd095c6e470ea35858ad7ef012910a23c9d3b8bef3f22");
            when(typesafeConfig.getBoolean(INTRA_VM_DISCOVERY_ENABLED)).thenReturn(intraVmDiscoveryEnabled);
            when(typesafeConfig.getBoolean(LOCAL_HOST_DISCOVERY_ENABLED)).thenReturn(localHostDiscoveryEnabled);
            when(typesafeConfig.getString(LOCAL_HOST_DISCOVERY_PATH)).thenReturn(localHostDiscoveryPathAsString);
            when(typesafeConfig.getDuration(LOCAL_HOST_DISCOVERY_LEASE_TIME)).thenReturn(localHostDiscoveryLeaseTime);
            when(typesafeConfig.getDuration(REMOTE_MESSAGE_COMPOSED_MESSAGE_TRANSFER_TIMEOUT)).thenReturn(composedMessageTransferTimeout);
            when(typesafeConfig.getBoolean(MONITORING_ENABLED)).thenReturn(monitoringEnabled);
            when(typesafeConfig.getString(MONITORING_HOST_TAG)).thenReturn(monitoringHostTag);
            when(typesafeConfig.getString(MONITORING_INFLUX_URI)).thenReturn(monitoringInfluxUri.toString());
            when(typesafeConfig.getString(MONITORING_INFLUX_USER)).thenReturn(monitoringInfluxUser);
            when(typesafeConfig.getString(MONITORING_INFLUX_PASSWORD)).thenReturn(monitoringInfluxPassword);
            when(typesafeConfig.getString(MONITORING_INFLUX_DATABASE)).thenReturn(monitoringInfluxDatabase);
            when(typesafeConfig.getDuration(MONITORING_INFLUX_REPORTING_FREQUENCY)).thenReturn(monitoringInfluxReportingFrequency);
            when(typesafeConfig.getObject(PLUGINS)).thenReturn(mock(ConfigObject.class));
            when(typesafeConfig.getStringList(MARSHALLING_INBOUND_ALLOWED_TYPES)).thenReturn(marshallingInboundAllowedTypes);
            when(typesafeConfig.getBoolean(MARSHALLING_INBOUND_ALLOW_ALL_PRIMITIVES)).thenReturn(marshallingInboundAllowAllPrimitives);
            when(typesafeConfig.getBoolean(MARSHALLING_INBOUND_ALLOW_ARRAY_OF_DEFINED_TYPES)).thenReturn(marshallingInboundAllowArrayOfDefinedTypes);
            when(typesafeConfig.getStringList(MARSHALLING_INBOUND_ALLOWED_PACKAGES)).thenReturn(marshallingInboundAllowedPackages);
            when(typesafeConfig.getStringList(MARSHALLING_OUTBOUND_ALLOWED_TYPES)).thenReturn(marshallingOutboundAllowedTypes);
            when(typesafeConfig.getBoolean(MARSHALLING_OUTBOUND_ALLOW_ALL_PRIMITIVES)).thenReturn(marshallingOutboundAllowAllPrimitives);
            when(typesafeConfig.getBoolean(MARSHALLING_OUTBOUND_ALLOW_ARRAY_OF_DEFINED_TYPES)).thenReturn(marshallingOutboundAllowArrayOfDefinedTypes);
            when(typesafeConfig.getStringList(MARSHALLING_OUTBOUND_ALLOWED_PACKAGES)).thenReturn(marshallingOutboundAllowedPackages);
            when(typesafeConfig.getObject(PLUGINS)).thenReturn(ConfigFactory.parseString("plugins { \"" + MyPlugin.class.getName() + "\" { enabled = true } }").getObject("plugins"));

            final DrasylConfig config = new DrasylConfig(typesafeConfig);

            assertEquals(networkId, config.getNetworkId());
            assertEquals(serverBindHost, config.getRemoteBindHost());
            assertEquals(serverBindPort, config.getRemoteBindPort());
            assertEquals(ProofOfWork.of(123), config.getIdentityProofOfWork());
            assertEquals(CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3"), config.getIdentityPublicKey());
            assertEquals(CompressedPrivateKey.of("0b01459ef93b2b7dc22794a3b9b7e8fac293399cf9add5b2375d9c357a64546d"), config.getIdentityPrivateKey());
            assertEquals(Paths.get("drasyl.identity.json"), config.getIdentityPath());
            assertEquals(serverEnabled, config.isRemoteEnabled());
            assertEquals(serverHandshakeTimeout, config.getRemotePingInterval());
            assertEquals(Set.of(), config.getRemoteEndpoints());
            assertEquals(remoteExposeEnabled, config.isRemoteExposeEnabled());
            assertEquals(remoteMessageMtu, config.getRemoteMessageMtu());
            assertEquals(remoteMessageMaxContentLength, config.getRemoteMessageMaxContentLength());
            assertEquals(remoteMessageHopLimit, config.getRemoteMessageHopLimit());
            assertEquals(superPeerEnabled, config.isRemoteSuperPeerEnabled());
            assertEquals(superPeerEndpoint, config.getRemoteSuperPeerEndpoint());
            assertEquals(intraVmDiscoveryEnabled, config.isIntraVmDiscoveryEnabled());
            assertEquals(localHostDiscoveryEnabled, config.isLocalHostDiscoveryEnabled());
            assertEquals(Path.of(localHostDiscoveryPathAsString), config.getLocalHostDiscoveryPath());
            assertEquals(localHostDiscoveryLeaseTime, config.getLocalHostDiscoveryLeaseTime());
            assertEquals(composedMessageTransferTimeout, config.getRemoteMessageComposedMessageTransferTimeout());
            assertEquals(monitoringEnabled, config.isMonitoringEnabled());
            assertEquals(monitoringHostTag, config.getMonitoringHostTag());
            assertEquals(monitoringInfluxUri, config.getMonitoringInfluxUri());
            assertEquals(monitoringInfluxUser, config.getMonitoringInfluxUser());
            assertEquals(monitoringInfluxPassword, config.getMonitoringInfluxPassword());
            assertEquals(monitoringInfluxDatabase, config.getMonitoringInfluxDatabase());
            assertEquals(monitoringInfluxReportingFrequency, config.getMonitoringInfluxReportingFrequency());
            assertEquals(marshallingInboundAllowedTypes, config.getMarshallingInboundAllowedTypes());
            assertEquals(marshallingInboundAllowAllPrimitives, config.isMarshallingInboundAllowAllPrimitives());
            assertEquals(marshallingInboundAllowArrayOfDefinedTypes, config.isMarshallingInboundAllowArrayOfDefinedTypes());
            assertEquals(marshallingInboundAllowedPackages, config.getMarshallingInboundAllowedPackages());
            assertEquals(marshallingOutboundAllowedTypes, config.getMarshallingOutboundAllowedTypes());
            assertEquals(marshallingOutboundAllowAllPrimitives, config.isMarshallingOutboundAllowAllPrimitives());
            assertEquals(marshallingOutboundAllowArrayOfDefinedTypes, config.isMarshallingOutboundAllowArrayOfDefinedTypes());
            assertEquals(marshallingOutboundAllowedPackages, config.getMarshallingOutboundAllowedPackages());
        }
    }

    @Nested
    class ToString {
        @Test
        void shouldMaskSecrets() throws CryptoException {
            identityPrivateKey = CompressedPrivateKey.of("07e98a2f8162a4002825f810c0fbd69b0c42bd9cb4f74a21bc7807bc5acb4f5f");

            final DrasylConfig config = new DrasylConfig(networkId, identityProofOfWork, identityPublicKey, identityPrivateKey, identityPath,
                    serverBindHost, serverEnabled, serverBindPort,
                    serverHandshakeTimeout, remotePingTimeout, remotePingCommunicationTimeout, remoteUniteMinInterval, remotePingMaxPeers, serverEndpoints,
                    remoteExposeEnabled, superPeerEnabled, superPeerEndpoint, remoteMessageMaxContentLength, remoteMessageHopLimit, composedMessageTransferTimeout,
                    remoteMessageMtu, intraVmDiscoveryEnabled, localHostDiscoveryEnabled, Path.of(localHostDiscoveryPathAsString),
                    localHostDiscoveryLeaseTime, monitoringEnabled, monitoringHostTag, monitoringInfluxUri, monitoringInfluxUser,
                    monitoringInfluxPassword, monitoringInfluxDatabase, monitoringInfluxReportingFrequency, plugins,
                    marshallingInboundAllowedTypes, marshallingInboundAllowAllPrimitives, marshallingInboundAllowArrayOfDefinedTypes, marshallingInboundAllowedPackages,
                    marshallingOutboundAllowedTypes, marshallingOutboundAllowAllPrimitives, marshallingOutboundAllowArrayOfDefinedTypes, marshallingOutboundAllowedPackages);

            assertThat(config.toString(), not(containsString(identityPrivateKey.toString())));
        }
    }

    @Nested
    class Equals {
        @Test
        void shouldReturnTrue() {
            final DrasylConfig config1 = DrasylConfig.newBuilder().build();
            final DrasylConfig config2 = DrasylConfig.newBuilder().build();

            assertEquals(config1, config2);
        }
    }

    @Nested
    class HashCode {
        @Test
        void shouldReturnTrue() {
            final DrasylConfig config1 = DrasylConfig.newBuilder().build();
            final DrasylConfig config2 = DrasylConfig.newBuilder().build();

            assertEquals(config1.hashCode(), config2.hashCode());
        }
    }

    @Nested
    class GetUri {
        @Test
        void shouldReturnUriAtPath() {
            final Config config = ConfigFactory.parseString("foo.bar = \"http://localhost.de\"");

            assertEquals(URI.create("http://localhost.de"), getURI(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionForInvalidValue() {
            final Config config = ConfigFactory.parseString("foo.bar = \"hallo world\"");

            assertThrows(ConfigException.class, () -> getURI(config, "foo.bar"));
        }
    }

    @Nested
    class GetPublicKey {
        @Test
        void shouldThrowExceptionForInvalidValue() {
            final Config config = ConfigFactory.parseString("foo.bar = bla");

            assertThrows(ConfigException.class, () -> getPublicKey(config, "foo.bar"));
        }
    }

    @Nested
    class GetPrivateKey {
        @Test
        void shouldThrowExceptionForInvalidValue() {
            final Config config = ConfigFactory.parseString("foo.bar = bla");

            assertThrows(ConfigException.class, () -> getPrivateKey(config, "foo.bar"));
        }
    }

    @Nested
    class GetPath {
        @Test
        void shouldThrowExceptionForInvalidValue() {
            final Config config = ConfigFactory.parseString("");

            assertThrows(ConfigException.class, () -> getPath(config, "foo.bar"));
        }
    }

    @Nested
    class GetShort {
        @Test
        void shouldThrowExceptionForInvalidValue() {
            final Config config = ConfigFactory.parseString("foo.bar = 123456789");

            assertThrows(ConfigException.class, () -> getByte(config, "foo.bar"));
        }
    }

    @Nested
    class GetEndpointList {
        @Test
        void shouldThrowExceptionForInvalidValue() {
            final Config config = ConfigFactory.parseString("foo.bar = [\"http://foo.bar\"]");

            assertThrows(ConfigException.class, () -> getEndpointList(config, "foo.bar"));
        }
    }

    @Nested
    class GetChannelInitializer {
        @Test
        void shouldThrowExceptionForInvalidValue() {
            final Config config = ConfigFactory.parseString("foo.bar = baz");

            assertThrows(ConfigException.class, () -> getChannelInitializer(config, "foo.bar"));
        }
    }

    @Nested
    class GetInetAddress {
        @Test
        void shouldThrowExceptionForInvalidValue() {
            final Config config = ConfigFactory.parseString("foo.bar = baz");

            assertThrows(ConfigException.class, () -> getInetAddress(config, "foo.bar"));
        }
    }

    @Nested
    class GetInetSocketAddress {
        @Test
        void shouldParseIPv4Address() {
            final Config config = ConfigFactory.parseString("foo.bar = \"203.0.113.149:22527\"");

            assertEquals(InetSocketAddress.createUnresolved("203.0.113.149", 22527), getInetSocketAddress(config, "foo.bar"));
        }

        @Test
        void shouldParseIPv6Address() {
            final Config config = ConfigFactory.parseString("foo.bar = \"[2001:db8::1]:8080\"");

            assertEquals(InetSocketAddress.createUnresolved("[2001:db8::1]", 8080), getInetSocketAddress(config, "foo.bar"));
        }

        @Test
        void shouldParseHostname() {
            final Config config = ConfigFactory.parseString("foo.bar = \"production.env.drasyl.org:1234\"");

            assertEquals(InetSocketAddress.createUnresolved("production.env.drasyl.org", 1234), getInetSocketAddress(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionAddressWithoutHostname() {
            final Config config = ConfigFactory.parseString("foo.bar = \"1234\"");

            assertThrows(ConfigException.class, () -> getInetSocketAddress(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionAddressWithoutPort() {
            final Config config = ConfigFactory.parseString("foo.bar = \"production.env.drasyl.org\"");

            assertThrows(ConfigException.class, () -> getInetSocketAddress(config, "foo.bar"));
        }
    }

    @Nested
    class GetPlugins {
        @Test
        void shouldThrowExceptionForNonExistingClasses() {
            final Config config = ConfigFactory.parseString("foo.bar { \"non.existing.class\" { enabled = true } }");

            assertThrows(ConfigException.class, () -> getPlugins(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionForClassWithMissingMethod() {
            final Config config = ConfigFactory.parseString("foo.bar { \"" + MyPluginWithMissingMethod.class.getName() + "\" { enabled = true } }");

            assertThrows(ConfigException.class, () -> getPlugins(config, "foo.bar"));
        }

        @Test
        void shouldThrowExceptionForClassWithInvocationTargetException() {
            final Config config = ConfigFactory.parseString("foo.bar { \"" + MyPluginWithInvocationTargetException.class.getName() + "\" { enabled = true } }");

            assertThrows(ConfigException.class, () -> getPlugins(config, "foo.bar"));
        }
    }

    @Nested
    class ParseFile {
        @Test
        void shouldReadConfigFromFile(@TempDir final Path dir) throws IOException {
            final Path path = Paths.get(dir.toString(), "drasyl.conf");
            Files.writeString(path, "drasyl.network.id = 1337", StandardOpenOption.CREATE);

            assertEquals(1337, DrasylConfig.parseFile(path.toFile()).getNetworkId());
        }
    }

    @Nested
    class ParseString {
        @Test
        void shouldReadConfigFromString() {
            assertEquals(1337, DrasylConfig.parseString("drasyl.network.id = 1337").getNetworkId());
        }
    }

    @Nested
    class Builder {
        @Test
        void shouldCreateCorrectConfig() {
            final DrasylConfig config = DrasylConfig.newBuilder()
                    .networkId(DEFAULT.getNetworkId())
                    .identityProofOfWork(DEFAULT.getIdentityProofOfWork())
                    .identityPublicKey(DEFAULT.getIdentityPublicKey())
                    .identityPrivateKey(DEFAULT.getIdentityPrivateKey())
                    .identityPath(DEFAULT.getIdentityPath())
                    .remoteBindHost(DEFAULT.getRemoteBindHost())
                    .remoteEnabled(DEFAULT.isRemoteEnabled())
                    .remoteBindPort(DEFAULT.getRemoteBindPort())
                    .remotePingInterval(DEFAULT.getRemotePingInterval())
                    .remotePingTimeout(DEFAULT.getRemotePingTimeout())
                    .remotePingCommunicationTimeout(DEFAULT.getRemotePingCommunicationTimeout())
                    .remoteUniteMinInterval(DEFAULT.getRemoteUniteMinInterval())
                    .remoteEndpoints(DEFAULT.getRemoteEndpoints())
                    .remoteExposeEnabled(DEFAULT.isRemoteExposeEnabled())
                    .remoteMessageMtu(DEFAULT.getRemoteMessageMtu())
                    .remoteMessageMaxContentLength(DEFAULT.getRemoteMessageMaxContentLength())
                    .remoteMessageComposedMessageTransferTimeout(DEFAULT.getRemoteMessageComposedMessageTransferTimeout())
                    .remoteMessageHopLimit(DEFAULT.getRemoteMessageHopLimit())
                    .remoteSuperPeerEnabled(DEFAULT.isRemoteSuperPeerEnabled())
                    .remoteSuperPeerEndpoint(DEFAULT.getRemoteSuperPeerEndpoint())
                    .intraVmDiscoveryEnabled(DEFAULT.isIntraVmDiscoveryEnabled())
                    .localHostDiscoveryEnabled(DEFAULT.isLocalHostDiscoveryEnabled())
                    .localHostDiscoveryPath(DEFAULT.getLocalHostDiscoveryPath())
                    .localHostDiscoveryLeaseTime(DEFAULT.getLocalHostDiscoveryLeaseTime())
                    .monitoringEnabled(DEFAULT.isMonitoringEnabled())
                    .monitoringHost(DEFAULT.getMonitoringHostTag())
                    .monitoringInfluxUri(DEFAULT.getMonitoringInfluxUri())
                    .monitoringInfluxUser(DEFAULT.getMonitoringInfluxUser())
                    .monitoringInfluxPassword(DEFAULT.getMonitoringInfluxPassword())
                    .monitoringInfluxDatabase(DEFAULT.getMonitoringInfluxDatabase())
                    .monitoringInfluxReportingFrequency(DEFAULT.getMonitoringInfluxReportingFrequency())
                    .plugins(DEFAULT.getPlugins())
                    .marshallingInboundAllowedTypes(DEFAULT.getMarshallingInboundAllowedTypes())
                    .marshallingInboundAllowAllPrimitives(DEFAULT.isMarshallingInboundAllowAllPrimitives())
                    .marshallingInboundAllowArrayOfDefinedTypes(DEFAULT.isMarshallingInboundAllowArrayOfDefinedTypes())
                    .marshallingInboundAllowedPackages(DEFAULT.getMarshallingInboundAllowedPackages())
                    .marshallingOutboundAllowedTypes(DEFAULT.getMarshallingOutboundAllowedTypes())
                    .marshallingOutboundAllowAllPrimitives(DEFAULT.isMarshallingOutboundAllowAllPrimitives())
                    .marshallingOutboundAllowArrayOfDefinedTypes(DEFAULT.isMarshallingOutboundAllowArrayOfDefinedTypes())
                    .marshallingOutboundAllowedPackages(DEFAULT.getMarshallingOutboundAllowedPackages())
                    .build();

            assertEquals(DEFAULT, config);
        }
    }
}