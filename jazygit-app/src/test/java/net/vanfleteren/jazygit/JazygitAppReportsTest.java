package net.vanfleteren.jazygit;

import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.layout.Rect;
import dev.tamboui.layout.Size;
import dev.tamboui.terminal.TestBackend;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.pilot.Pilot;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Preview;
import net.vanfleteren.jazygit.ui.CommitsPanel;
import net.vanfleteren.jazygit.ui.FilesPanel;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.Theme;
import org.junit.jupiter.api.Test;

/**
 * The view reports back what the widgets know after a frame, and nothing once the model agrees with them.
 */
class JazygitAppReportsTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final Size SIZE = new Size(200, 50);

    private final List<Sha> commits = new SampleData().commits().get().map(commit -> commit.sha());

    @Test
    void theHighlightedCommitIsReportedAfterTheFrameAndThenNothing() throws Exception {
        App app = new App();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, ticking())) {
            app.start(testRunner);
            SettledPilot.focus(testRunner, CommitsPanel.ID);
            awaitModel(testRunner, app, model -> shownCommit(model).contains(commits.get(0)));

            SettledPilot.of(testRunner).press(KeyCode.DOWN);

            awaitModel(testRunner, app, model -> shownCommit(model).contains(commits.get(1)));
            assertEquals(List.empty(), onRenderThread(testRunner, app::reports));
        } finally {
            app.onStop();
        }
    }

    @Test
    void aClickOnAPaneWithoutTheFocusGivesItTheFocusAndHighlightsTheRowClickedOn() throws Exception {
        App app = new App();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, ticking())) {
            app.start(testRunner);
            awaitModel(testRunner, app, model -> shownCommit(model).contains(commits.get(0)));
            Pilot pilot = SettledPilot.of(testRunner);
            Rect list = pilot.findElement(CommitsPanel.ID);

            // The second commit.
            pilot.mousePress(MouseButton.LEFT, list.left() + 3, list.top() + 1);

            assertEquals(CommitsPanel.ID, onRenderThread(testRunner, () -> focusedId(testRunner)));
            awaitModel(testRunner, app, model -> shownCommit(model).contains(commits.get(1)));

            Rect files = pilot.findElement(FilesPanel.ID);
            pilot.mousePress(MouseButton.LEFT, files.left() + 3, files.top());

            assertEquals(FilesPanel.ID, onRenderThread(testRunner, () -> focusedId(testRunner)));
        } finally {
            app.onStop();
        }
    }

    private static String focusedId(ToolkitTestRunner testRunner) {
        return testRunner.runner().focusManager().focusedId();
    }

    /**
     * As the test runner sets the terminal up, but with ticks, as the app has: what reaches the render thread
     * other than an event, like the reports, is only drawn on the next one.
     */
    private static TuiConfig ticking() {
        return TuiConfig.builder()
                .rawMode(false)
                .alternateScreen(false)
                .hideCursor(false)
                .mouseCapture(false)
                .shutdownHook(false)
                .pollTimeout(Duration.ofMillis(10))
                .tickRate(Duration.ofMillis(40))
                .backend(new TestBackend(SIZE.width(), SIZE.height()))
                .build();
    }

    private static Option<Sha> shownCommit(Model model) {
        return model.repoView().commitDetail().map(Preview::key);
    }

    /**
     * Waits until the model is as {@code expected} says: the reports and the loads they start reach it on later
     * turns of the render thread.
     */
    private static void awaitModel(ToolkitTestRunner testRunner, App app, Function<Model, Boolean> expected)
            throws Exception {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (!onRenderThread(testRunner, () -> expected.apply(app.model()))) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("the model did not get there within " + TIMEOUT);
            }
            Thread.sleep(20);
        }
    }

    private static <T> T onRenderThread(ToolkitTestRunner testRunner, Supplier<T> read) throws Exception {
        CompletableFuture<T> value = new CompletableFuture<>();
        testRunner.runner().runOnRenderThread(() -> value.complete(read.get()));
        return value.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * The app on the test's runner instead of one of its own, which only {@link #run()} makes.
     */
    private static final class App extends JazygitApp {

        private final AtomicReference<ToolkitRunner> runner = new AtomicReference<>();

        App() {
            super(
                    new Env(new SampleData(), StubGitHub.empty()),
                    SettingsStore.Loaded.DEFAULT,
                    Option.none(),
                    Theme.createEngine().get());
        }

        void start(ToolkitTestRunner testRunner) {
            testRunner.runner().runOnRenderThread(() -> {
                runner.set(testRunner.runner());
                onStart();
            });
            SettledPilot.settle(testRunner);
        }

        /**
         * Nothing until the app is started on the runner, as the test runner draws a frame as it starts.
         */
        Element frame() {
            return runner.get() == null ? text("") : render();
        }

        @Override
        protected ToolkitRunner runner() {
            return runner.get();
        }
    }
}
