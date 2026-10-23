package net.vanfleteren.jazygit;

import static net.vanfleteren.jazygit.TestApp.SIZE;
import static net.vanfleteren.jazygit.TestApp.awaitModel;
import static net.vanfleteren.jazygit.TestApp.focusedId;
import static net.vanfleteren.jazygit.TestApp.onRenderThread;
import static net.vanfleteren.jazygit.TestApp.tickless;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.layout.Rect;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.pilot.Pilot;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import net.vanfleteren.jazygit.feature.help.HelpDialog;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Preview;
import net.vanfleteren.jazygit.ui.BranchesPanel;
import net.vanfleteren.jazygit.ui.CommitsPanel;
import net.vanfleteren.jazygit.ui.FilesPanel;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.TimeText;
import org.junit.jupiter.api.Test;

/**
 * The view reports back what the widgets know after a frame, and nothing once the model agrees with them; and what
 * the model does not know, like the focus and the spinners, still gets drawn without ticks.
 */
class JazygitAppReportsTest {

    private static final Duration QUIET = Duration.ofMillis(300);

    private static final Duration SPINNING = Duration.ofSeconds(1);

    private static final BranchName MAIN = new BranchName("main");

    private final List<Sha> commits = new SampleData().commits().get().map(commit -> commit.sha());

    @Test
    void theHighlightedCommitIsReportedAfterTheFrameAndThenNothing() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
            app.start(testRunner);
            SettledPilot.focus(testRunner, CommitsPanel.ID, app.drawnAt::size);
            awaitModel(testRunner, app, model -> shownCommit(model).contains(commits.get(0)));

            SettledPilot.of(testRunner, app.drawnAt::size).press(KeyCode.DOWN);

            awaitModel(testRunner, app, model -> shownCommit(model).contains(commits.get(1)));
            assertEquals(List.empty(), onRenderThread(testRunner, app::reports));
        } finally {
            app.onStop();
        }
    }

    @Test
    void aClickOnAPaneWithoutTheFocusGivesItTheFocusAndHighlightsTheRowClickedOn() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
            app.start(testRunner);
            awaitModel(testRunner, app, model -> shownCommit(model).contains(commits.get(0)));
            Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);
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
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
            app.start(testRunner);
            SettledPilot.focus(testRunner, CommitsPanel.ID, app.drawnAt::size);
            awaitModel(testRunner, app, model -> shownCommit(model).contains(commits.get(0)));
            Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);

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
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
            app.start(testRunner);
            SettledPilot.focus(testRunner, CommitsPanel.ID, app.drawnAt::size);
            Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);
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
        TestApp app = new TestApp(new Env(new PullsSlowly(pulled), StubGitHub.empty()));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
            app.start(testRunner);
            SettledPilot.focus(testRunner, BranchesPanel.ID, app.drawnAt::size);
            awaitModel(
                    testRunner,
                    app,
                    model -> model.repo().branches().toOption().exists(branches -> !branches.isEmpty()));
            SettledPilot.of(testRunner, app.drawnAt::size).press('p');
            awaitModel(testRunner, app, model -> model.jobs().busy().containsKey(new BranchRef.Local(MAIN)));

            Instant from = Instant.now();
            Thread.sleep(SPINNING.toMillis());

            // The model's own clock draws one frame every 500 ms; the spinner asks for one every 120 ms.
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
            return Either.right(List.of(Fixtures.branch(
                    MAIN,
                    true,
                    new Sha("aaaa"),
                    Instant.EPOCH,
                    true,
                    true,
                    Option.of(new Upstream(new BranchRef.Remote(new RemoteBranch("origin", MAIN)), 0, 1)))));
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

    private static Option<Sha> shownCommit(Model model) {
        return model.repoView().commitDetail().map(Preview::key);
    }
}
