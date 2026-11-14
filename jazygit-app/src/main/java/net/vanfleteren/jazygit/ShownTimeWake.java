package net.vanfleteren.jazygit;

import dev.tamboui.toolkit.app.ToolkitRunner;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * The one wake scheduled for when a time the screen shows changes next (ADR 124): told after each frame when that is,
 * it keeps the wake scheduled for the same moment, puts one for another moment in its place, and cancels it for none.
 * Used on the render thread only.
 */
final class ShownTimeWake {

    private final Supplier<ToolkitRunner> runner;
    private final Runnable wake;
    private Option<Instant> at = Option.none();
    private Option<ToolkitRunner.ScheduledAction> scheduled = Option.none();

    /**
     * @param wake what is run on the render thread at the moment: it tells the model the time, and draws a frame
     */
    ShownTimeWake(Supplier<ToolkitRunner> runner, Runnable wake) {
        this.runner = runner;
        this.wake = wake;
    }

    /**
     * Wakes at {@code next}, if anything changes, counted from {@code now}.
     */
    void at(Option<Instant> next, Instant now) {
        if (next.equals(at)) {
            return;
        }
        scheduled.forEach(ToolkitRunner.ScheduledAction::cancel);
        at = next;
        scheduled = next.map(moment ->
                runner.get().schedule(() -> runner.get().runOnRenderThread(this::woken), until(now, moment)));
    }

    private void woken() {
        at = Option.none();
        scheduled = Option.none();
        wake.run();
    }

    /**
     * How long from {@code now} to just past {@code moment}, so the time told then has reached it.
     */
    private static Duration until(Instant now, Instant moment) {
        Duration left = Duration.between(now, moment).plusMillis(1);
        return left.isNegative() ? Duration.ZERO : left;
    }
}
