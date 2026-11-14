package net.vanfleteren.jazygit;

import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;

/**
 * The one wake scheduled for when a time the screen shows changes next (ADR 124): told after each frame when that is,
 * it keeps the wake scheduled for the same moment, puts one for another moment in its place, and cancels it for none.
 * Used on the render thread only.
 */
final class ShownTimeWake {

    private final Scheduler scheduler;
    private final Runnable wake;
    private Option<Instant> at = Option.none();
    private Option<Runnable> cancel = Option.none();

    /**
     * @param wake what is run on the render thread at the moment: it tells the model the time, and draws a frame
     */
    ShownTimeWake(Scheduler scheduler, Runnable wake) {
        this.scheduler = scheduler;
        this.wake = wake;
    }

    /**
     * Wakes at {@code next}, if anything changes, counted from {@code now}.
     */
    void at(Option<Instant> next, Instant now) {
        if (next.equals(at)) {
            return;
        }
        cancel.forEach(Runnable::run);
        at = next;
        cancel = next.map(moment -> scheduler.schedule(() -> woken(moment), until(now, moment)));
    }

    /**
     * The wake for {@code moment} came; one that another took the place of while it was on its way to the render
     * thread does nothing, as its cancel came too late.
     */
    private void woken(Instant moment) {
        if (!at.contains(moment)) {
            return;
        }
        at = Option.none();
        cancel = Option.none();
        wake.run();
    }

    /**
     * How long from {@code now} to just past {@code moment}, so the time told then has reached it.
     */
    private static Duration until(Instant now, Instant moment) {
        Duration left = Duration.between(now, moment).plusMillis(1);
        return left.isNegative() ? Duration.ZERO : left;
    }

    /**
     * Runs an action on the render thread after a delay.
     */
    @FunctionalInterface
    interface Scheduler {

        /**
         * Runs {@code action} on the render thread once {@code delay} went by.
         *
         * @return what cancels it, if it did not run yet
         */
        Runnable schedule(Runnable action, Duration delay);
    }
}
