package net.vanfleteren.jazygit;

import dev.tamboui.toolkit.app.ToolkitRunner;
import io.vavr.collection.List;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.feature.fetch.FetchMsg;
import net.vanfleteren.jazygit.feature.github.checks.ChecksMsg;
import net.vanfleteren.jazygit.feature.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.gh.timing.CheckTimings;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.prs.PrOverviewMsg;
import net.vanfleteren.jazygit.ui.TimeText;

/**
 * What the app does every so often, without input: the messages it dispatches, and the spinners it turns.
 */
final class Timers {

    // In milliseconds; the acceptance tests tick faster, as each of their steps waits for the app to see a change.
    static final Duration REFRESH_INTERVAL = Duration.ofMillis(Long.getLong("jazygit.refreshInterval", 1000));
    static final Duration FETCH_INTERVAL = Duration.ofMinutes(1);
    // How often GitHub is asked how the merges it does in the background are going (ADR 24).
    static final Duration MERGE_CHECK_INTERVAL = Duration.ofSeconds(2);
    // What is dispatched every so often: the time, the repository read again, and what the remotes and GitHub have.
    static final List<Timer> ALL = List.of(
            new Timer(TimeText.CLOCK_INTERVAL, Msg.Clock::new),
            new Timer(REFRESH_INTERVAL, Msg.Tick::new),
            new Timer(FETCH_INTERVAL, FetchMsg.Tick::new),
            new Timer(FETCH_INTERVAL, PrOverviewMsg.Tick::new),
            // The dialogs that list checks read them only while one is not done, or once a minute (ADR 34).
            new Timer(CheckTimings.READ_WHILE_PENDING, PullRequestMsg.Tick::new),
            new Timer(CheckTimings.READ_WHILE_PENDING, ChecksMsg.Tick::new),
            new Timer(MERGE_CHECK_INTERVAL, PullRequestMsg.MergeTick::new));

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

    /**
     * Dispatches what {@code msg} makes every {@code interval}.
     */
    record Timer(Duration interval, Supplier<Msg> msg) {}
}
