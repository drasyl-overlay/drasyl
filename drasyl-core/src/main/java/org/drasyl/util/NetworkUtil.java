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

import org.drasyl.crypto.Crypto;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import static org.drasyl.util.UrlUtil.createUrl;

/**
 * Utility class for network-related operations.
 */
public final class NetworkUtil {
    /**
     * The minimum server port number.
     */
    public static final int MIN_PORT_NUMBER = 0;
    /**
     * The maximum server port number.
     */
    public static final int MAX_PORT_NUMBER = 65535;
    private static final Logger LOG = LoggerFactory.getLogger(NetworkUtil.class);
    /**
     * Services for external IPv4 address detection.
     */
    private static final URL[] EXTERNAL_IPV4_ADDRESS_SERVICES = {
            createUrl("https://checkip.amazonaws.com"),
            createUrl("https://ipv4.icanhazip.com"),
            createUrl("https://ipv4.wtfismyip.com/text"),
            createUrl("https://myexternalip.com/raw"),
            createUrl("https://ipecho.net/plain"),
            createUrl("https://ifconfig.me/ip"),
            createUrl("https://ipv4.ipecho.roebert.eu")
    };
    /**
     * Services for external IPv6 address detection.
     */
    private static final URL[] EXTERNAL_IPV6_ADDRESS_SERVICES = {
            createUrl("https://ipv6.icanhazip.com"),
            createUrl("https://ipv6.wtfismyip.com/text"),
            createUrl("https://ipv6.ipecho.roebert.eu")
    };

    private NetworkUtil() {
        // util class
    }

    /**
     * Determines the external IPv4 address.
     * <p>
     * Note: This is a blocking method, because it connects to external server that may react slowly
     * or not at all.
     *
     * @return the external IPv4 address or {@code null} in case of error
     */
    public static Inet4Address getExternalIPv4Address() {
        return getExternalIPAddress(EXTERNAL_IPV4_ADDRESS_SERVICES);
    }

    /**
     * Determines the external IP address.
     * <p>
     * Note: This is a blocking method, because it connects to external server that may react slowly
     * or not at all.
     *
     * @return the external IP address or {@code null} in case of error
     */
    private static <T extends InetAddress> T getExternalIPAddress(final URL[] providers) {
        // distribute requests across all available ip check tools
        final int randomOffset = Crypto.randomNumber(providers.length);
        for (int i = 0; i < providers.length; i++) {
            final URL provider = providers[(i + randomOffset) % providers.length];

            try {
                final URLConnection connection = provider.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                LOG.debug("Request external ip address from service '{}'...", provider);

                try (final BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    final String response = reader.readLine();
                    @SuppressWarnings("unchecked") final T address = (T) InetAddress.getByName(response);
                    if (!address.isLoopbackAddress() && !address.isAnyLocalAddress() && !address.isSiteLocalAddress()) {
                        LOG.debug("Got external ip address '{}' from service '{}'", address, provider);
                        return address;
                    }
                }
            }
            catch (final IOException | ClassCastException e) {
                // do nothing, skip to next provider
            }
        }

        // no provider was successful
        return null;
    }

    /**
     * Determines the external IPv6 address.
     * <p>
     * Note: This is a blocking method, because it connects to external server that may react slowly
     * or not at all.
     *
     * @return the external IPv6 address or {@code null} in case of error
     */
    public static Inet6Address getExternalIPv6Address() {
        return getExternalIPAddress(EXTERNAL_IPV6_ADDRESS_SERVICES);
    }

    /**
     * Checks to see if a specific port is available.
     *
     * <p>
     * Source: <a href= "https://svn.apache.org/viewvc/camel/trunk/components/camel-test/src/main/java/org/apache/camel/test/AvailablePortFinder.java?view=markup#l130">Apache
     * camel</a>
     * </p>
     *
     * @param port the port number to check for availability
     * @return <tt>true</tt> if the port is available, or <tt>false</tt> if not
     * @throws IllegalArgumentException is thrown if the port number is out of range
     */
    @SuppressWarnings("java:S4818")
    public static boolean available(final int port) {
        if (!isValidPort(port)) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }

        try (final ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);

            return true;
        }
        catch (final IOException e) {
            // Do nothing
        }

        return false;
    }

    /**
     * Checks if a port is valid or not.
     *
     * @param port port that should be validated.
     * @return true if valid, otherwise false
     */
    public static boolean isValidPort(final int port) {
        return port >= MIN_PORT_NUMBER && port <= MAX_PORT_NUMBER;
    }

    /**
     * Checks to see if a specific host:port is available.
     *
     * @param host host name or IP address to check for availability
     * @param port the port number to check for availability
     * @return <tt>true</tt> if the host:port is available, or <tt>false</tt> if not
     * @throws IllegalArgumentException is thrown if the port number is out of range
     */
    public static boolean alive(final String host, final int port) {
        if (!isValidPort(port)) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }

        try (final Socket s = new Socket(host, port)) {
            final PrintWriter out = new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8);
            out.println("GET / HTTP/1.1");

            return true;
        }
        catch (final IOException e) {
            // Do nothing
        }

        return false;
    }

    /**
     * Returns a list of the IP addresses of all network interfaces of the local computer. If no IP
     * addresses can be obtained, the loopback address is returned.
     *
     * @return list of IP addresses of all network interfaces of local computer or loopback address
     * if no address can be obtained
     */
    public static Set<InetAddress> getAddresses() {
        try {
            final Set<InetAddress> addresses = new HashSet<>();

            final Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                final NetworkInterface iface = ifaces.nextElement();

                if (!iface.isUp() || iface.isLoopback() || iface.isPointToPoint()) {
                    continue;
                }

                final Enumeration<InetAddress> ifaceAddresses = iface.getInetAddresses();
                while (ifaceAddresses.hasMoreElements()) {
                    final InetAddress address = ifaceAddresses.nextElement();
                    if (isValidNonSpecialIPAddress(address)) {
                        addresses.add(address);
                    }
                }
            }

            return addresses;
        }
        catch (final SocketException e) {
            return Set.of(InetAddress.getLoopbackAddress());
        }
    }

    /**
     * Check if the given address is a normal IP address that is neither a loopback, link local nor
     * multicast address.
     *
     * @param address the address that should be checked
     * @return true if the address is a normal IP address, false otherwise
     */
    public static boolean isValidNonSpecialIPAddress(final InetAddress address) {
        final boolean hasScope;
        if (address instanceof Inet6Address) {
            final Inet6Address inet6Address = (Inet6Address) address;
            hasScope = inet6Address.getScopeId() != 0 || inet6Address.getScopedInterface() != null;
        }
        else {
            hasScope = false;
        }

        return !hasScope &&
                !address.isLoopbackAddress() &&
                !address.isLinkLocalAddress() &&
                !address.isMulticastAddress() &&
                ((address instanceof Inet4Address) || (address instanceof Inet6Address));
    }

    /**
     * Returns the local host name. If no host name can be determined, {@code null} is returned.
     *
     * @return the local host name. If no host name can be determined, {@code null} is returned.
     */
    public static String getLocalHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        }
        catch (final UnknownHostException e) {
            return null;
        }
    }

    /**
     * Creates a {@link InetAddress} by parsing the given string.
     *
     * <p> This convenience factory method works as if by invoking the {@link
     * InetAddress#getByName(java.lang.String)} constructor; any {@link UnknownHostException} thrown
     * by the constructor is caught and wrapped in a new {@link IllegalArgumentException} object,
     * which is then thrown.
     *
     * <p> This method is provided for use in situations where it is known that
     * the given string is a legal InetAddress, for example for InetAddress constants declared
     * within a program, and so it would be considered a programming error for the string not to
     * parse as such. The constructors, which throw {@link UnknownHostException} directly, should be
     * used in situations where a InetAddress is being constructed from user input or from some
     * other source that may be prone to errors.  </p>
     *
     * @param str The string to be parsed into a InetAddress
     * @return The new InetAddress
     * @throws IllegalArgumentException if no IP address for the {@code str} could be found, or if a
     *                                  scope_id was specified for a global IPv6 address.
     */
    public static InetAddress createInetAddress(final String str) {
        try {
            return InetAddress.getByName(str);
        }
        catch (final UnknownHostException x) {
            throw new IllegalArgumentException(x.getMessage(), x);
        }
    }

    /**
     * Returns the network prefix length for given address. This is also known as the subnet mask in
     * the context of IPv4 addresses. Typical IPv4 values would be 8 (255.0.0.0), 16 (255.255.0.0)
     * or 24 (255.255.255.0).
     * <p>
     * Typical IPv6 values would be 128 (::1/128) or 10 (fe80::203:baff:fe27:1243/10)
     *
     * @param address The {@code InetAddress} to search with.
     * @return a {@code short} representing the prefix length for the subnet of that address or
     * {@code -1} if there is no network interface with given IP address.
     * @throws NullPointerException If the specified address is {@code null}.
     */
    public static short getNetworkPrefixLength(final InetAddress address) {
        try {
            final NetworkInterface networkInterface = NetworkInterface.getByInetAddress(address);
            if (networkInterface != null) {
                for (final InterfaceAddress ifaceAddress : networkInterface.getInterfaceAddresses()) {
                    if (address.equals(ifaceAddress.getAddress())) {
                        return ifaceAddress.getNetworkPrefixLength();
                    }
                }
            }

            // no network interface with the specified IP address found
            return -1;
        }
        catch (final SocketException e) {
            // I/O error occurs
            return -1;
        }
    }

    /**
     * Checks if two given addresses are in the same network.
     *
     * @param a    first address
     * @param b    second address
     * @param mask the network mask in CIDR notation
     * @return true if the two given addresses are in the same network, false otherwise
     */
    public static boolean sameNetwork(final InetAddress a, final InetAddress b, final short mask) {
        return sameNetwork(a.getAddress(), b.getAddress(), mask);
    }

    /**
     * Checks if two given addresses are in the same network.
     *
     * @param x    first address
     * @param y    second address
     * @param cidr the network mask in CIDR notation
     * @return true if the two given addresses are in the same network, false otherwise
     */
    public static boolean sameNetwork(final byte[] x, final byte[] y, final short cidr) {
        if (x == y) {
            return true;
        }
        if (x == null || y == null) {
            return false;
        }
        if (x.length != y.length) {
            return false;
        }

        // converting the CIDR mask to byte array of correct length
        final byte[] mask = cidr2Netmask(cidr, x.length);

        for (int i = 0; i < x.length; i++) {
            if ((x[i] & mask[i]) != (y[i] & mask[i])) {
                return false;
            }
        }

        return true;
    }

    /**
     * Converts a given CIDR mask to valid IPv4 or IPv6 network mask.
     *
     * @param cidr       the cidr
     * @param byteLength the final mask length (IPv4 = 4 bytes, IPv6 = 16 bytes)
     * @return the CIDR as network mask byte array
     * @throws IllegalArgumentException if the {@code byteLength} argument is invalid
     */
    public static byte[] cidr2Netmask(final short cidr, final int byteLength) {
        if (byteLength != 4 && byteLength != 16) {
            throw new IllegalArgumentException("A valid IP address has always 4 or 16 bytes.");
        }

        final boolean[] bitSet = new boolean[byteLength * 8];
        Arrays.fill(bitSet, 0, cidr, true);

        final byte[] mask = new byte[byteLength];
        for (int i = 0; i < mask.length; i++) {
            byte b = 0;
            for (int j = 0; j < 8; j++) {
                if (bitSet[8 * i + j]) {
                    b |= 1 << (7 - j);
                }
            }
            mask[i] = b;
        }

        return mask;
    }
}