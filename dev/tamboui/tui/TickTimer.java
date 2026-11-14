/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.tui;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Tells a runner when its tick periods pass.
 * <p>
 * Times are read from {@link System#nanoTime()}, which only moves forward. The system clock
 * can be set back or forward while an app runs, by NTP or by hand: ticks timed with it would
 * stop until the clock is back where it was.
 * <p>
 * Not thread-safe: a runner uses it from its scheduler task only.
 */
final class TickTimer {

    private final long periodNanos;
    private final LongSupplier nanoTime;
    private long nextPeriodNanos;

    /**
     * Creates a timer whose first period starts now.
     *
     * @param period the tick period
     */
    TickTimer(Duration period) {
        this(period, System::nanoTime);
    }

    /**
     * Creates a timer whose first period starts now, reading times from the given clock.
     *
     * @param period   the tick period
     * @param nanoTime the clock, in nanoseconds as {@link System#nanoTime()} returns them
     */
    TickTimer(Duration period, LongSupplier nanoTime) {
        this.periodNanos = period.toNanos();
        this.nanoTime = nanoTime;
        this.nextPeriodNanos = nanoTime.getAsLong() + periodNanos;
    }

    /**
     * Returns whether the current tick period has passed. If it has, the next period starts
     * where it ended, so a late call does not delay the periods after it.
     *
     * @return true if the current period has passed
     */
    boolean periodPassed() {
        // Compared by their difference, as nanoTime values may overflow
        if (nanoTime.getAsLong() - nextPeriodNanos < 0) {
            return false;
        }
        nextPeriodNanos += periodNanos;
        return true;
    }
}
