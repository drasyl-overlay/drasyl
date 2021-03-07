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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Holder for the JSON serializer and JSON deserializer.
 */
public final class JSONUtil {
    public static final ObjectMapper JACKSON_MAPPER;
    public static final ObjectWriter JACKSON_WRITER;
    public static final ObjectReader JACKSON_READER;

    static {
        JACKSON_MAPPER = new ObjectMapper();
        JACKSON_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        JACKSON_WRITER = JACKSON_MAPPER.writer();
        JACKSON_READER = JACKSON_MAPPER.reader();
    }

    private JSONUtil() {
        // util class
    }
}
