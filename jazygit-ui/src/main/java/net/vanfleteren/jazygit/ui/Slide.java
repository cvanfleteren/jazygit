package net.vanfleteren.jazygit.ui;

import java.time.Duration;
import java.time.Instant;

/**
 * How what slides on the screen gets where it is going: it eases out, fast at first and slowing down, and is there
 * after {@link #LENGTH} (ADR 81). The left column slides to its width so, and the panes in it to their heights
 * (ADR 92).
 */
public final class Slide {

    /**
     * How long what slides takes to get where it is going.
     */
    public static final Duration LENGTH = Duration.ofMillis(150);

    private Slide() {}

    /**
     * Where something on its way from {@code from} to {@code to} since {@code start} is at {@code now}.
     */
    public static int at(int from, int to, Instant start, Instant now) {
        double elapsed = Duration.between(start, now).toNanos() / (double) LENGTH.toNanos();
        double eased = 1 - Math.pow(1 - Math.clamp(elapsed, 0, 1), 3);
        return (int) Math.round(from + (to - from) * eased);
    }
}
