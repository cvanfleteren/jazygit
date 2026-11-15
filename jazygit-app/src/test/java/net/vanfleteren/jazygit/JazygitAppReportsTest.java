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
import dev.tamboui.tui.event.KeyModifiers;
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
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.branches.Branches;
import net.vanfleteren.jazygit.git.branches.Remotes;
import net.vanfleteren.jazygit.git.branches.StubBranches;
import net.vanfleteren.jazygit.git.branches.StubRemotes;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Fixtures;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Upstream;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.git.process.GitFailure;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.view.Preview;
import net.vanfleteren.jazygit.ui.CommandLogPanel;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.branches.BranchesPanel;
import net.vanfleteren.jazygit.ui.commits.CommitsPanel;
import net.vanfleteren.jazygit.ui.files.FilesPanel;
import net.vanfleteren.jazygit.ui.text.TimeText;
import org.junit.jupiter.api.Test;

/**
 * The view reports back what the widgets know after a frame, and nothing once the model agrees with them; and what
 * the model does not know, like the focus and the spinners, still gets drawn without ticks.
 */
class JazygitAppReportsTest {

    private static final Duration QUIET = Duration.ofMillis(300);

    private static final Duration SPINNING = Duration.ofSeconds(1);

    private static final BranchName MAIN = new BranchName("main");

    private final List<Sha> commits = SampleData.headLog().get().map(commit -> commit.sha());

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

            // The fetch line, which takes no focus.
            pilot.mousePress(MouseButton.LEFT, 150, SIZE.height() - 1);
            pilot.pause();

            assertEquals(CommitsPanel.ID, onRenderThread(testRunner, () -> focusedId(testRunner)));
            pilot.press(KeyCode.DOWN);
            awaitModel(testRunner, app, model -> shownCommit(model).contains(commits.get(1)));
        } finally {
            app.onStop();
        }
    }

    @Test
    void atGivesTheCommandLogTheFocus() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
            app.start(testRunner);
            SettledPilot.focus(testRunner, CommitsPanel.ID, app.drawnAt::size);
            awaitModel(testRunner, app, model -> shownCommit(model).contains(commits.get(0)));
            Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);
            int before = pilot.findElement(CommandLogPanel.ID).height();

            pilot.press('@');
            pilot.pause();

            assertEquals(CommandLogPanel.ID, onRenderThread(testRunner, () -> focusedId(testRunner)));
            // Empty, it is as tall as without the focus: there is nothing more to show.
            assertEquals(before, pilot.findElement(CommandLogPanel.ID).height());
        } finally {
            app.onStop();
        }
    }

    @Test
    void aPopupOpenedFromTheCommandLogGivesItTheFocusBack() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
            app.start(testRunner);
            SettledPilot.focus(testRunner, CommitsPanel.ID, app.drawnAt::size);
            awaitModel(testRunner, app, model -> shownCommit(model).contains(commits.get(0)));
            Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);
            pilot.press('@');
            pilot.pause();

            // The settings, a popup a global key opens wherever the focus is.
            pilot.press('s', KeyModifiers.CTRL);
            awaitModel(testRunner, app, model -> model.popup().isDefined());
            pilot.press(KeyCode.ESCAPE);
            awaitModel(testRunner, app, model -> model.popup().isEmpty());
            pilot.pause();

            assertEquals(CommandLogPanel.ID, onRenderThread(testRunner, () -> focusedId(testRunner)));
            // The model still knows the pane, which Escape in the log gives the focus back to.
            pilot.press(KeyCode.ESCAPE);
            pilot.pause();
            assertEquals(CommitsPanel.ID, onRenderThread(testRunner, () -> focusedId(testRunner)));
        } finally {
            app.onStop();
        }
    }

    @Test
    void atChosenInTheHelpGivesTheCommandLogTheFocusOnceTheHelpIsClosed() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
            app.start(testRunner);
            SettledPilot.focus(testRunner, CommitsPanel.ID, app.drawnAt::size);
            awaitModel(testRunner, app, model -> shownCommit(model).contains(commits.get(0)));
            Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);
            pilot.press('?');
            awaitModel(testRunner, app, model -> model.popup().isDefined());

            // A key pressed in the help runs it, as Enter on its row does.
            pilot.press('@');
            awaitModel(testRunner, app, model -> model.popup().isEmpty());
            pilot.pause();

            assertEquals(CommandLogPanel.ID, onRenderThread(testRunner, () -> focusedId(testRunner)));
        } finally {
            app.onStop();
        }
    }

    @Test
    void aClickOnTheCommandLogGivesItTheFocusAndEscapeGivesItBack() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, SIZE, tickless())) {
            app.start(testRunner);
            SettledPilot.focus(testRunner, CommitsPanel.ID, app.drawnAt::size);
            awaitModel(testRunner, app, model -> shownCommit(model).contains(commits.get(0)));
            Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);

            Rect log = pilot.findElement(CommandLogPanel.ID);
            pilot.mousePress(MouseButton.LEFT, log.left() + 3, log.top() + 1);
            pilot.pause();

            assertEquals(CommandLogPanel.ID, onRenderThread(testRunner, () -> focusedId(testRunner)));
            // The model keeps the pane the focus was in: the content panel still shows its commit.
            assertTrue(app.model().shownPane().contains(CommitsPanel.PANE));

            pilot.press(KeyCode.ESCAPE);
            pilot.pause();

            assertEquals(CommitsPanel.ID, onRenderThread(testRunner, () -> focusedId(testRunner)));
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
        TestApp app = new TestApp(new Env(new PullsSlowly(pulled).git(), StubGitHub.of()));
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
    private record PullsSlowly(CountDownLatch pulled) {

        public Branches branches() {
            return new StubBranches() {
                @Override
                public Either<GitError, List<Branch>> local() {
                    return Either.right(List.of(Fixtures.branch(
                            MAIN,
                            true,
                            new Sha("aaaa"),
                            Instant.EPOCH,
                            true,
                            true,
                            Option.of(new Upstream(new BranchRef.Remote(new RemoteBranch("origin", MAIN)), 0, 1)))));
                }
            };
        }

        public Remotes remotes() {
            return new StubRemotes() {
                @Override
                public Either<GitFailure, List<ExecutedCommand>> pull() {
                    try {
                        pulled.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    return StubGit.ran();
                }
            };
        }

        Git git() {
            return StubGit.of().withBranches(branches()).withRemotes(remotes());
        }
    }

    private static Option<Sha> shownCommit(Model model) {
        return model.repoView().preview().commitDetail().map(Preview::key);
    }
}
