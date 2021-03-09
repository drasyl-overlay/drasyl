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

import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.lang.reflect.Field;

/**
 * Utility class that can be used to detect properties specific to the current runtime environment,
 * such as Java version and the availability of the {@code sun.misc.Unsafe} object.
 */
public final class PlatformDependent {
    private static final Logger LOG = LoggerFactory.getLogger(PlatformDependent.class);
    private static final int JAVA_VERSION = detectJavaVersion();
    // See https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/
    // ImageInfo.java
    private static final boolean RUNNING_IN_NATIVE_IMAGE = System.getProperty("org.graalvm.nativeimage.imagecode") != null;

    private PlatformDependent() {
        // util class
    }

    /**
     * Returns the Java version.
     *
     * @return the Java version
     */
    public static int javaVersion() {
        return JAVA_VERSION;
    }

    /**
     * Returns {@code true} if access to {@link sun.misc.Unsafe#staticFieldOffset(Field)} is
     * supported, {@code false} otherwise.
     *
     * @return {@code true} if access to {@link sun.misc.Unsafe#staticFieldOffset(Field)} is
     * supported, {@code false} otherwise
     */
    public static boolean unsafeStaticFieldOffsetSupported() {
        return !RUNNING_IN_NATIVE_IMAGE;
    }

    static int javaSpecificationVersion() {
        return Integer.parseInt(System.getProperty("java.specification.version", "11"));
    }

    private static int detectJavaVersion() {
        final int majorVersion = javaSpecificationVersion();

        LOG.debug("Java version: {}", majorVersion);

        return majorVersion;
    }
}
