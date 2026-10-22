package net.vanfleteren.jazygit;

import static dev.tamboui.toolkit.Toolkit.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.feature.help.HelpDialog;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Preview;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.CommitsPanel;
import net.vanfleteren.jazygit.ui.FilesPanel;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.TimeText;
import org.junit.jupiter.api.Test;

/**
 * The view reports back what the widgets know after a frame, and nothing once the model agrees with them; and what
 * the model does not know, like the focus and the spinners, still gets drawn without ticks.
 */
class JazygitAppReportsTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final Size SIZE = new Size(200, 50);

    private static final Duration QUIET = Duration.ofMillis(300);

    private static final Duration SPINNING = Duration.ofSeconds(1);

    private static final Branch.Name MAIN = new Branch.Name("main");

    private final List<Sha> commits = new SampleData().commits().get().map(commit -> commit.sha());

    @Test
    void theHighlightedCommitIsReportedAfterTheFrameAndThenNothing() throws Exception {
        App app = new App();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
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
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
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

    @Test
    void aClickOnNothingLeavesTheFocusWhereItWas() throws Exception {
        App app = new App();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
            app.start(testRunner);
            SettledPilot.focus(testRunner, CommitsPanel.ID);
            awaitModel(testRunner, app, model -> shownCommit(model).contains(commits.get(0)));
            Pilot pilot = SettledPilot.of(testRunner);

            // The command log, which takes no focus.
            pilot.mousePress(MouseButton.LEFT, 150, SIZE.height() - 3);
            pilot.pause();

            assertEquals(CommitsPanel.ID, onRenderThread(testRunner, () -> focusedId(testRunner)));
            pilot.press(KeyCode.DOWN);
            awaitModel(testRunner, app, model -> shownCommit(model).contains(commits.get(1)));
        } finally {
            app.onStop();
        }
    }

    @Test
    void aPopupThatClosesGivesTheFocusBackInTheNextFrame() throws Exception {
        App app = new App();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
            app.start(testRunner);
            SettledPilot.focus(testRunner, CommitsPanel.ID);
            Pilot pilot = SettledPilot.of(testRunner);
            pilot.press('?');
            assertEquals(HelpDialog.ID, onRenderThread(testRunner, () -> app.drawnWith));
            // The loads the popup's reports start each draw a frame once they are done, which would hide a missing one.
            pilot.pause(QUIET);

            pilot.press(KeyCode.ESCAPE);
            // The frame the key draws gives the focus back, and asks for the next one, which is drawn with it.
            pilot.pause();

            assertEquals(CommitsPanel.ID, onRenderThread(testRunner, () -> app.drawnWith));
        } finally {
            app.onStop();
        }
    }

    @Test
    void theSpinnerOfABusyBranchTurnsWithoutInput() throws Exception {
        CountDownLatch pulled = new CountDownLatch(1);
        App app = new App(new Env(new PullsSlowly(pulled), StubGitHub.empty()));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
            app.start(testRunner);
            SettledPilot.focus(testRunner, BranchesPanel.ID);
            awaitModel(
                    testRunner,
                    app,
                    model -> model.repo().branches().toOption().exists(branches -> !branches.isEmpty()));
            SettledPilot.of(testRunner).press('p');
            awaitModel(testRunner, app, model -> model.jobs().busy().containsKey(new BranchRef.Local(MAIN)));

            Instant from = Instant.now();
            Thread.sleep(SPINNING.toMillis());

            // The model's own tick draws one frame every 750 ms; the spinner asks for one every 120 ms.
            long turns = app.drawnAt.stream()
                    .filter(at -> at.isAfter(from))
                    .map(TimeText::spinnerFrame)
                    .distinct()
                    .count();
            assertTrue(turns >= 4, "the spinner showed " + turns + " frames in " + SPINNING);
        } finally {
            pulled.countDown();
            app.onStop();
        }
    }

    /**
     * A repository on {@code main}, which follows {@code origin/main}, where a pull runs until {@code pulled}.
     */
    private record PullsSlowly(CountDownLatch pulled) implements StubGitProvider {

        @Override
        public Either<ProcessError, List<Branch>> branches() {
            return Either.right(List.of(new Branch(
                    MAIN,
                    true,
                    new Sha("aaaa"),
                    Instant.EPOCH,
                    true,
                    true,
                    Option.of(new Upstream(new RemoteBranch("origin", MAIN), 0, 1)))));
        }

        @Override
        public Either<GitFailure<GitError>, List<ExecutedCommand>> pull() {
            try {
                pulled.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return StubGitProvider.ran();
        }
    }

    private static String focusedId(ToolkitTestRunner testRunner) {
        return testRunner.runner().focusManager().focusedId();
    }

    /**
     * As the test runner sets the terminal up, and without ticks, as the app has: what reaches the render thread
     * other than an event, like the loads and the reports, is only drawn when the app asks for a frame.
     */
    private static TuiConfig tickless() {
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
        // When each frame was drawn, and the focus the last one was drawn with; frames are drawn on the render
        // thread.
        private final java.util.List<Instant> drawnAt = new CopyOnWriteArrayList<>();
        private String drawnWith;

        App() {
            this(new Env(new SampleData(), StubGitHub.empty()));
        }

        App(Env env) {
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
            SettledPilot.settle(testRunner);
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
    }
}
