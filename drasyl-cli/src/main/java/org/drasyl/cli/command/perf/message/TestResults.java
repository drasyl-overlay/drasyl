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
package org.drasyl.cli.command.perf.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;

import static org.drasyl.util.NumberUtil.numberToHumanData;
import static org.drasyl.util.NumberUtil.numberToHumanDataRate;

@SuppressWarnings("java:S1192")
public class TestResults implements PerfMessage {
    public static final float PERCENT = 0.01F;
    public static final int MICROSECONDS = 1_000_000_000;
    private final long messageSize;
    private final long testStartTime;
    private final LongAdder totalMessages;
    private final LongAdder lostMessages;
    private final LongAdder outOfOrderMessages;
    private final long startTime;
    private long stopTime;

    @SuppressWarnings("unused")
    @JsonCreator
    TestResults(@JsonProperty("messageSize") final long messageSize,
                @JsonProperty("testStartTime") final long testStartTime,
                @JsonProperty("totalMessages") final long totalMessages,
                @JsonProperty("lostMessages") final long lostMessages,
                @JsonProperty("outOfOrderMessages") final long outOfOrderMessages,
                @JsonProperty("startTime") final long startTime,
                @JsonProperty("stopTime") final long stopTime) {
        this.messageSize = messageSize;
        this.testStartTime = testStartTime;
        if (testStartTime < 1) {
            throw new IllegalArgumentException("testStartTime must be greater than 0");
        }
        this.totalMessages = new LongAdder();
        this.totalMessages.add(totalMessages);
        this.lostMessages = new LongAdder();
        this.lostMessages.add(lostMessages);
        this.outOfOrderMessages = new LongAdder();
        this.outOfOrderMessages.add(outOfOrderMessages);
        this.startTime = startTime;
        this.stopTime = stopTime;
    }

    public TestResults(final long messageSize,
                       final long testStartTime,
                       final long startTime) {
        this(messageSize, testStartTime, 0, 0, 0, startTime, 0);
    }

    public void stop(final long currentTime) {
        if (stopTime == 0) {
            stopTime = currentTime;
        }
    }

    @SuppressWarnings("unused")
    public long getMessageSize() {
        return messageSize;
    }

    public long getTestStartTime() {
        return testStartTime;
    }

    public long getTotalMessages() {
        return totalMessages.sum();
    }

    public long getLostMessages() {
        return lostMessages.sum();
    }

    public long getOutOfOrderMessages() {
        return outOfOrderMessages.sum();
    }

    public long getStartTime() {
        return startTime;
    }

    public long getStopTime() {
        return stopTime;
    }

    public void incrementTotalMessages() {
        incrementTotalMessages(1);
    }

    public void incrementTotalMessages(final long x) {
        if (stopTime != 0) {
            throw new IllegalStateException("stopped!");
        }

        totalMessages.add(x);
    }

    public void incrementLostMessages() {
        if (stopTime != 0) {
            throw new IllegalStateException("stopped!");
        }

        lostMessages.increment();
    }

    public void incrementOutOfOrderMessages() {
        if (stopTime != 0) {
            throw new IllegalStateException("stopped!");
        }

        outOfOrderMessages.increment();
    }

    public String print() {
        if (stopTime == 0) {
            throw new IllegalStateException("not stopped!");
        }

        final double relativeIntervalStartTime = ((double) startTime - testStartTime) / MICROSECONDS;
        final double relativeIntervalStopTime = ((double) stopTime - testStartTime) / MICROSECONDS;
        final double intervalDuration = relativeIntervalStopTime - relativeIntervalStartTime;
        final long currentTotalMessages = totalMessages.sum();
        final String transfer = numberToHumanData(currentTotalMessages * messageSize);
        final String bitrate = numberToHumanDataRate(currentTotalMessages * messageSize * 8 * (1 / intervalDuration));
        final double lostPercent;
        if (currentTotalMessages > 0) {
            lostPercent = (double) lostMessages.sum() / currentTotalMessages / PERCENT;
        }
        else {
            lostPercent = 0;
        }

        String result = String.format((Locale) null, "%,6.2f - %,6.2f sec      %7s      %10s      %19s", relativeIntervalStartTime, relativeIntervalStopTime, transfer, bitrate, String.format((Locale) null, "%d/%d (%.2f%%)", lostMessages.sum(), totalMessages.sum(), lostPercent));
        if (getOutOfOrderMessages() > 0) {
            result += String.format((Locale) null, "%n  %5d messages received out-of-order", getOutOfOrderMessages());
        }
        return result;
    }

    public void add(final TestResults other) {
        totalMessages.add(other.getTotalMessages());
        lostMessages.add(other.getLostMessages());
        outOfOrderMessages.add(other.getOutOfOrderMessages());
    }

    public void adjustResults(final TestResults otherResults) {
        lostMessages.reset();
        final long newLostMessages = otherResults.getTotalMessages() - getTotalMessages();
        if (newLostMessages > 0) {
            lostMessages.add(newLostMessages);
        }

        totalMessages.reset();
        totalMessages.add(otherResults.getTotalMessages());
    }
}
