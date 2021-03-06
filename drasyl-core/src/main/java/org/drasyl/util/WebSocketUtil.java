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
package org.drasyl.util;

import java.net.URI;

/**
 * Utility class for operations on websocket {@link URI}s (e.g. ws://foo.bar).
 */
public final class WebSocketUtil {
    public static final int WS_PORT = 80;
    public static final int WSS_PORT = 443;

    private WebSocketUtil() {
        // util class
    }

    /**
     * @param uri the URI to be used for determining the WebSocket URI
     * @return the port in the WebSocket URI. If no custom port is specified, the protocol default
     * port is assumed
     * @throws IllegalArgumentException if no port could be determined
     * @throws NullPointerException     if {@code uri} is {@code null}
     */
    public static int webSocketPort(final URI uri) {
        final int port = uri.getPort();

        // port was included in URI
        if (port != -1) {
            return port;
        }

        // Fallback: Use protocol standard ports
        final String scheme = uri.getScheme();
        if (scheme != null) {
            if ("ws".equals(scheme)) {
                return WS_PORT;
            }
            else if ("wss".equals(scheme)) {
                return WSS_PORT;
            }
        }

        throw new IllegalArgumentException("Unable to determine websocket port for uri '" + uri + "'. Not a websocket uri?");
    }

    /**
     * @param uri the URI to be checked
     * @return <code>true</code> if <code>uri</code> is a WebSocket Secure URI. Otherwise, returns
     * <code>false</code>
     * @throws NullPointerException if {@code uri} is {@code null}
     */
    public static boolean isWebSocketSecureURI(final URI uri) {
        return "wss".equals(uri.getScheme());
    }

    /**
     * @param uri the URI to be checked
     * @return <code>true</code> if <code>uri</code> is a non-secure Websocket URI. Otherwise,
     * returns <code>false</code>
     * @throws NullPointerException if {@code uri} is {@code null}
     */
    public static boolean isWebSocketNonSecureURI(final URI uri) {
        return "ws".equals(uri.getScheme());
    }

    /**
     * @param uri the URI to be checked
     * @return <code>true</code> if <code>uri</code> is Websocket URI. Otherwise, returns
     * <code>false</code>
     * @throws NullPointerException if {@code uri} is {@code null}
     */
    public static boolean isWebSocketURI(final URI uri) {
        return isWebSocketNonSecureURI(uri) || isWebSocketSecureURI(uri);
    }
}
