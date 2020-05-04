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

package org.drasyl.core.common.tools;

import org.drasyl.core.common.messages.Leave;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

public final class NetworkTool {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * The minimum server port number.
     */
    public static final int MIN_PORT_NUMBER = 0;

    /**
     * The maximum server port number.
     */
    public static final int MAX_PORT_NUMBER = 65535;

    /**
     * Domains for IP detection.
     */
    private static final String[] ipCheckTools = {"http://checkip.amazonaws.com", "http://ipv4.icanhazip.com",
            "http://bot.whatismyipaddress.com", "http://myexternalip.com/raw", "http://ipecho.net/plain"};

    private NetworkTool() {
    }

    /**
     * Determines the external IP address.
     *
     * @return the external IP address
     * @throws IOException if the IP address cloud not resolve
     */
    public static String getExternalIPAddress() throws IOException {
        String ipAddress = null;
        IOException ex = null;
        for (String checker : ipCheckTools) {
            try {
                URL checkerURL = new URL(checker);

                try (BufferedReader in = new BufferedReader(new InputStreamReader(checkerURL.openStream()))) {
                    ipAddress = in.readLine();
                    if (!isEmpty(ipAddress)) {
                        break;
                    }
                }
            } catch (IOException e) {
                ex = e;
            }
        }
        if (isEmpty(ipAddress)) {
            if (ex != null) {
                throw ex;
            }
            throw new IOException("External IP address couldn't be resolved.");
        }

        return ipAddress;
    }

    /**
     * Checks to see if a specific port is available.
     *
     * <p>
     * Source: <a href=
     * "https://svn.apache.org/viewvc/camel/trunk/components/camel-test/src/main/java/org/apache/camel/test/AvailablePortFinder.java?view=markup#l130">Apache
     * camel</a>
     * </p>
     *
     * @param port the port number to check for availability
     * @return <tt>true</tt> if the port is available, or <tt>false</tt> if not
     * @throws IllegalArgumentException is thrown if the port number is out of range
     */
    public static boolean available(int port) {
        if (!isValidPort(port))
            throw new IllegalArgumentException("Invalid port: " + port);

        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);

            return true;
        } catch (IOException e) {
            // Do nothing
        }

        return false;
    }

    /**
     * Checks to see if a specific host:port is available.
     *
     * @param host host name or IP address to check for availability
     * @param port the port number to check for availability
     * @return <tt>true</tt> if the host:port is available, or <tt>false</tt> if not
     * @throws IllegalArgumentException is thrown if the port number is out of range
     */
    public static boolean alive(String host, int port) {
        if (!isValidPort(port))
            throw new IllegalArgumentException("Invalid port: " + port);

        try (Socket s = new Socket(host, port)) {
            PrintWriter out = new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8);
            out.println(JSON_MAPPER.writeValueAsString(new Leave()));

            return true;
        } catch (IOException e) {
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
    public static boolean isValidPort(int port) {
        return port >= MIN_PORT_NUMBER && port <= MAX_PORT_NUMBER;
    }

    /**
     * Checks if a CharSequence is empty ("") or null.
     *
     * @param cs the CharSequence to check, may be null
     */
    private static boolean isEmpty(final CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    /**
     * Returns a list of the IP addresses of all network interfaces of the local computer. If no IP
     * addresses can be obtained, 127.0.0.1 is returned.
     *
     * @return
     */
    public static Set<String> getAddresses() {
        try {
            Set<String> addresses = new HashSet<>();

            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();

                if (!iface.isUp() || iface.isLoopback() || iface.isPointToPoint()) {
                    continue;
                }

                Enumeration<InetAddress> ifaceAddresses = iface.getInetAddresses();
                while (ifaceAddresses.hasMoreElements()) {
                    InetAddress ifaceAddress = ifaceAddresses.nextElement();
                    String address = getAddressByInetAddress(ifaceAddress);
                    if (address != null) {
                        addresses.add(address);
                    }
                }
            }

            return addresses;
        }
        catch (SocketException e) {
            return new HashSet<>(Collections.singletonList("127.0.0.1"));
        }
    }

    private static String getAddressByInetAddress(InetAddress ifaceAddress) {
        if (ifaceAddress.isLoopbackAddress() || ifaceAddress.isLinkLocalAddress() || ifaceAddress.isMulticastAddress()) {
            return null;
        }

        if (ifaceAddress instanceof Inet4Address) {
            return ifaceAddress.getHostAddress();
        }
        else if (ifaceAddress instanceof Inet6Address) {
            String hostAddress = ifaceAddress.getHostAddress();

            // remove scope
            int percent = hostAddress.indexOf('%');
            if (percent != -1) {
                hostAddress = hostAddress.substring(0, percent);
            }

            return "[" + hostAddress + "]";
        }
        else {
            return null;
        }
    }
}