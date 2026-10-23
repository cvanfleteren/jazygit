package net.vanfleteren.jazygit;

import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.layout.Size;
import dev.tamboui.terminal.TestBackend;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.TuiConfig;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.Theme;

/**
 * The app on the test's runner instead of one of its own, which only {@link #run()} makes.
 */
final class TestApp extends JazygitApp {

    static final Duration TIMEOUT = Duration.ofSeconds(5);

    static final Size SIZE = new Size(200, 50);

    private final AtomicReference<ToolkitRunner> runner = new AtomicReference<>();
    // When each frame was drawn, and the focus the last one was drawn with; frames are drawn on the render
    // thread.
    final java.util.List<Instant> drawnAt = new CopyOnWriteArrayList<>();
    String drawnWith;

    TestApp() {
        this(new Env(new SampleData(), StubGitHub.empty()));
    }

    TestApp(Env env) {
        super(
                env,
                SettingsStore.Loaded.DEFAULT,
                Option.none(),
                Theme.createEngine().get());
    }

    void start(ToolkitTestRunner testRunner) {
        testRunner.runner().runOnRenderThread(() -> {
            runner.set(testRunner.runner());
            onStart();
        });
        SettledPilot.settle(testRunner, drawnAt::size);
    }

    /**
     * Nothing until the app is started on the runner, as the test runner draws a frame as it starts.
     */
    Element frame() {
        if (runner.get() == null) {
            return text("");
        }
        drawnAt.add(Instant.now());
        drawnWith = runner.get().focusManager().focusedId();
        return render();
    }

    @Override
    protected ToolkitRunner runner() {
        return runner.get();
    }

    static String focusedId(ToolkitTestRunner testRunner) {
        return testRunner.runner().focusManager().focusedId();
    }

    /**
     * As the test runner sets the terminal up, and without ticks, as the app has: what reaches the render thread
     * other than an event, like the loads and the reports, is only drawn when the app asks for a frame.
     */
    static TuiConfig tickless() {
        return TuiConfig.builder()
                .rawMode(false)
                .alternateScreen(false)
                .hideCursor(false)
                .mouseCapture(false)
                .shutdownHook(false)
                .pollTimeout(Duration.ofMillis(10))
                .noTick()
                .backend(new TestBackend(SIZE.width(), SIZE.height()))
                .build();
    }

    /**
     * Waits until the model is as {@code expected} says: the reports and the loads they start reach it on later
     * turns of the render thread.
     */
    static void awaitModel(ToolkitTestRunner testRunner, TestApp app, Function<Model, Boolean> expected)
            throws Exception {
        await(() -> onRenderThread(testRunner, () -> expected.apply(app.model())), "the model got there");
    }

    /**
     * Waits until {@code condition} holds, failing when it does not within {@link #TIMEOUT}.
     *
     * @param what what holds then, for the failure
     */
    static void await(Condition condition, String what) throws Exception {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!condition.holds()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("not within " + TIMEOUT + ": " + what);
            }
            Thread.sleep(20);
        }
    }

    static <T> T onRenderThread(ToolkitTestRunner testRunner, Supplier<T> read) throws Exception {
        CompletableFuture<T> value = new CompletableFuture<>();
        testRunner.runner().runOnRenderThread(() -> value.complete(read.get()));
        return value.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * What {@link #await} waits for; checking it may throw, as reading on the render thread does.
     */
    @FunctionalInterface
    interface Condition {

        boolean holds() throws Exception;
    }
}
