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
package org.drasyl.serialization;

import org.drasyl.AbstractBenchmark;
import org.drasyl.remote.protocol.Protocol.PublicHeader;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;

@State(Scope.Benchmark)
public class ProtobufSerializerBenchmark extends AbstractBenchmark {
    private ProtobufSerializer serializer;
    private PublicHeader o;
    private byte[] bytes;

    @Setup
    public void setup() {
        serializer = new ProtobufSerializer();
        o = PublicHeader.newBuilder().setNetworkId(1337).build();
        bytes = o.toByteArray();
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void toByteArray(Blackhole blackhole) {
        try {
            blackhole.consume(serializer.toByteArray(o));
        }
        catch (final IOException e) {
            handleUnexpectedException(e);
        }
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void fromByteArray(Blackhole blackhole) {
        try {
            blackhole.consume(serializer.fromByteArray(bytes, PublicHeader.class));
        }
        catch (final IOException e) {
            handleUnexpectedException(e);
        }
    }
}
