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

import io.netty.util.ReferenceCounted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ReferenceCountUtil {
    private static final Logger LOG = LoggerFactory.getLogger(ReferenceCountUtil.class);

    private ReferenceCountUtil() {
    }

    /**
     * Try to call {@link ReferenceCounted#release()} if the specified message implements {@link
     * ReferenceCounted} and is not already released. If the specified message doesn't implement
     * {@link ReferenceCounted}, this method does nothing.
     */
    public static boolean release(final Object o) {
        if (o instanceof ReferenceCounted) {
            final ReferenceCounted ref = (ReferenceCounted) o;

            if (ref.refCnt() != 0) {
                return ref.release();
            }

            return true;
        }

        return false;
    }

    /**
     * Try to call {@link ReferenceCounted#release()} if the specified message implements {@link
     * ReferenceCounted} and is not already released. If the specified message doesn't implement
     * {@link ReferenceCounted}, this method does nothing. Unlike {@link #release(Object)} this
     * method catches an exception raised by {@link ReferenceCounted#release()} and logs it, rather
     * than rethrowing it to the caller.  It is usually recommended to use {@link #release(Object)}
     * instead, unless you absolutely need to swallow an exception.
     */
    @SuppressWarnings({ "java:S1181" })
    public static void safeRelease(final Object o) {
        try {
            release(o);
        }
        catch (final Throwable t) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Failed to release a reference counted object: {}", o, t);
            }
        }
    }
}
