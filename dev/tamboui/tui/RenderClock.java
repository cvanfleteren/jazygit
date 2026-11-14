/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.tui;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Measures the real time between renders, so animations advance by actual elapsed
 * time rather than by a fixed per-tick amount.
 * <p>
 * This mirrors the model used by the ratatui effects library (tachyonfx), where the
 * host measures the time since the last draw and advances effects by it. Measuring at
 * the render means an input-driven redraw between two ticks advances an animation only
 * by the little time that really passed, so the animation does not speed up while keys
 * are pressed or the mouse moves.
 * <p>
 * Times are read from {@link System#nanoTime()}, which only moves forward, so setting
 * the system clock does not affect animations and the elapsed time is never negative.
 * The time below a millisecond is carried to the next render, so there is no truncation
 * drift. A gap longer than {@value #MAX_ELAPSED_MILLIS} ms, such as a laptop sleep or a
 * debugger pause, is clamped, so an animation does not jump far ahead after it.
 * <p>
 * Not thread-safe: a runner uses it from its render thread only.
 */
public final class RenderClock {

    /** The longest gap, in milliseconds, that one render advances an animation by. */
    public static final long MAX_ELAPSED_MILLIS = 250;

    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final long MAX_ELAPSED_NANOS = MAX_ELAPSED_MILLIS * NANOS_PER_MILLI;

    private final LongSupplier nanoTime;
    private boolean started;
    private long lastNanos;
    private long carryNanos;

    /**
     * Creates a clock that reads {@link System#nanoTime()}.
     */
    public RenderClock() {
        this(System::nanoTime);
    }

    /**
     * Creates a clock that reads the given time source, for deterministic rendering and tests.
     *
     * @param nanoTime the time source, in nanoseconds as {@link System#nanoTime()} returns them
     */
    public RenderClock(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    /**
     * Returns the real time since the previous call, as whole milliseconds with the
     * remainder carried to the next call. The first call after creation or {@link #reset()}
     * returns zero. The result is never negative and never more than
     * {@value #MAX_ELAPSED_MILLIS} ms.
     *
     * @return the time to advance animations by for this render
     */
    public Duration tick() {
        long now = nanoTime.getAsLong();
        if (!started) {
            started = true;
            lastNanos = now;
            return Duration.ZERO;
        }
        // Compared by their difference, as nanoTime values may overflow
        long delta = now - lastNanos;
        lastNanos = now;
        if (delta < 0) {
            delta = 0;
        } else if (delta > MAX_ELAPSED_NANOS) {
            delta = MAX_ELAPSED_NANOS;
        }
        carryNanos += delta;
        long millis = carryNanos / NANOS_PER_MILLI;
        carryNanos -= millis * NANOS_PER_MILLI;
        return Duration.ofMillis(millis);
    }

    /**
     * Forgets the timing, so the next {@link #tick()} returns zero and starts a fresh
     * animation. A runner calls this while nothing is animating, so an animation that
     * starts later does not pick up the idle time before it.
     */
    public void reset() {
        started = false;
        carryNanos = 0;
    }
}
