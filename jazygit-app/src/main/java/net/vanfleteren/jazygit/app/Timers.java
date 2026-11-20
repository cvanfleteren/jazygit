package net.vanfleteren.jazygit.app;

import dev.tamboui.toolkit.app.ToolkitRunner;
import io.vavr.collection.List;
import java.time.Duration;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.app.keys.Features;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TimerMsg;
import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.Feature.Timer;
import net.vanfleteren.jazygit.kernel.ui.text.TimeText;

/**
 * What the app does every so often, without input: the messages it dispatches, and the spinners it turns.
 */
final class Timers {

    // In milliseconds; the acceptance tests tick faster, as each of their steps waits for the app to see a change.
    // Every 3 s rather than every second: reading the status was most of what an idle app cost (ADR 125).
    static final Duration REFRESH_INTERVAL = Duration.ofMillis(Long.getLong("jazygit.refreshInterval", 3000));
    // What is dispatched every so often: the repository read again, and what each feature asks for, as what the remotes
    // and the forge have. The time comes when a time shown changes (ADR 124).
    static final List<Timer> ALL =
            List.of(new Timer(REFRESH_INTERVAL, TimerMsg.Tick::new)).appendAll(Features.ALL.flatMap(Feature::timers));

    private Timers() {}

    /**
     * Dispatches what each of {@link #ALL} makes every so often, and runs {@code turnSpinners} every frame of a
     * spinner, all on the render thread of {@code runner}.
     */
    static void start(ToolkitRunner runner, Consumer<Msg> dispatch, Runnable turnSpinners) {
        ALL.forEach(timer -> every(
                runner, timer.interval(), () -> dispatch.accept(timer.msg().get())));
        every(runner, TimeText.SPINNER_FRAME, turnSpinners);
    }

    /**
     * Runs {@code action} on the render thread every {@code interval}.
     */
    private static void every(ToolkitRunner runner, Duration interval, Runnable action) {
        runner.scheduleRepeating(() -> runner.runOnRenderThread(action), interval);
    }
}
