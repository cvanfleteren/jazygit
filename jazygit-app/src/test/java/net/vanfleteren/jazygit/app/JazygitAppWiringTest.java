package net.vanfleteren.jazygit.app;

import static net.vanfleteren.jazygit.app.TestApp.await;
import static net.vanfleteren.jazygit.app.TestApp.awaitModel;
import static net.vanfleteren.jazygit.app.TestApp.focusedId;
import static net.vanfleteren.jazygit.app.TestApp.onRenderThread;
import static net.vanfleteren.jazygit.app.TestApp.tickless;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.terminal.TestBackend;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.pilot.Pilot;
import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import net.vanfleteren.jazygit.branches.branch.stack.StackMsg;
import net.vanfleteren.jazygit.changes.commit.CommitDialog;
import net.vanfleteren.jazygit.changes.commit.create.CommitPopup;
import net.vanfleteren.jazygit.changes.fetch.FetchMsg;
import net.vanfleteren.jazygit.chrome.help.HelpPopup;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.StubGit;
import net.vanfleteren.jazygit.git.files.Status;
import net.vanfleteren.jazygit.git.files.StubStatus;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.github.checks.ChecksMsg;
import net.vanfleteren.jazygit.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.TimerMsg;
import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.state.github.prs.PrOverviewMsg;
import net.vanfleteren.jazygit.state.load.AheadUpdate;
import net.vanfleteren.jazygit.state.preferences.Screen;
import net.vanfleteren.jazygit.state.view.CommitFilesView;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.branches.BranchesPanel;
import net.vanfleteren.jazygit.ui.commits.CommitsPanel;
import net.vanfleteren.jazygit.ui.files.FilesPanel;
import net.vanfleteren.jazygit.ui.keys.Keymap;
import net.vanfleteren.jazygit.ui.keys.Scopes;
import org.junit.jupiter.api.Test;

/**
 * What the app wires up as it starts: the keys that work wherever the focus is, what it does every so often, and
 * the focus of an open popup.
 */
class JazygitAppWiringTest {

    @Test
    void aGlobalKeyWorksWhereverTheFocusIs() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, TestApp.SIZE, tickless())) {
            app.start(testRunner);
            SettledPilot.focus(testRunner, CommitsPanel.ID, app.drawnAt::size);

            SettledPilot.of(testRunner, app.drawnAt::size).press('p', KeyModifiers.CTRL);

            awaitModel(testRunner, app, model -> model.screen() == Screen.PULL_REQUESTS);
        } finally {
            app.onStop();
        }
    }

    @Test
    void shiftFOnTheMainBranchListsAllItsFilesAndGivesThemTheFocus() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, TestApp.SIZE, tickless())) {
            app.start(testRunner);
            awaitModel(testRunner, app, model -> AheadUpdate.main(model).isDefined());
            SettledPilot.focus(testRunner, BranchesPanel.ID, app.drawnAt::size);

            // The main branch is the first one listed.
            SettledPilot.of(testRunner, app.drawnAt::size).press('F');

            awaitModel(
                    testRunner,
                    app,
                    model -> model.repoView()
                            .files()
                            .commitFiles()
                            .exists(view -> view.of() instanceof Changes.OfTip && view.allFiles()));
            await(
                    () -> FilesPanel.ID.equals(onRenderThread(testRunner, () -> focusedId(testRunner))),
                    "the files focused");
            // What the main branch follows, the working tree it is checked out in, did not take their place as the
            // focus moved (ADR 141).
            SettledPilot.of(testRunner, app.drawnAt::size);
            assertEquals(
                    Boolean.TRUE,
                    onRenderThread(
                            testRunner,
                            () -> app.model()
                                    .repoView()
                                    .files()
                                    .commitFiles()
                                    .exists(view -> view.of() instanceof Changes.OfTip && !view.followed())));
        } finally {
            app.onStop();
        }
    }

    @Test
    void theFilesFollowTheHighlightedCommitAndEnterAndEscapeGoInAndBack() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, TestApp.SIZE, tickless())) {
            app.start(testRunner);
            awaitModel(
                    testRunner,
                    app,
                    model -> model.repoView().preview().commitDetail().isDefined());
            SettledPilot.focus(testRunner, CommitsPanel.ID, app.drawnAt::size);

            // The files of the highlighted commit are listed, and the focus stays in the commits (ADR 141).
            awaitModel(
                    testRunner,
                    app,
                    model -> model.repoView()
                            .files()
                            .commitFiles()
                            .exists(view -> view.followed() && view.of() instanceof Changes.OfCommit));
            assertEquals(CommitsPanel.ID, onRenderThread(testRunner, () -> focusedId(testRunner)));

            SettledPilot.of(testRunner, app.drawnAt::size).press(KeyCode.ENTER);
            await(
                    () -> FilesPanel.ID.equals(onRenderThread(testRunner, () -> focusedId(testRunner))),
                    "the files focused");

            SettledPilot.of(testRunner, app.drawnAt::size).press(KeyCode.ESCAPE);
            await(
                    () -> CommitsPanel.ID.equals(onRenderThread(testRunner, () -> focusedId(testRunner))),
                    "the commits focused again");
            awaitModel(
                    testRunner,
                    app,
                    model -> model.repoView().files().commitFiles().exists(CommitFilesView::followed));
        } finally {
            app.onStop();
        }
    }

    @Test
    void theFilesEnterOpenedStayThroughOtherListsUntilEscapeInTheirList() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, TestApp.SIZE, tickless())) {
            app.start(testRunner);
            awaitModel(
                    testRunner,
                    app,
                    model -> model.repoView().preview().commitDetail().isDefined());
            SettledPilot.focus(testRunner, CommitsPanel.ID, app.drawnAt::size);
            SettledPilot.of(testRunner, app.drawnAt::size).press(KeyCode.ENTER);
            await(
                    () -> FilesPanel.ID.equals(onRenderThread(testRunner, () -> focusedId(testRunner))),
                    "the files focused");
            Changes opened = onRenderThread(
                    testRunner,
                    () -> app.model().repoView().files().commitFiles().get().of());

            // The branches, whose checked-out branch follows the working tree, do not take their place (ADR 149).
            SettledPilot.focus(testRunner, BranchesPanel.ID, app.drawnAt::size);
            SettledPilot.of(testRunner, app.drawnAt::size).pause();
            SettledPilot.of(testRunner, app.drawnAt::size).pause();
            assertEquals(
                    Option.of(opened),
                    onRenderThread(
                            testRunner,
                            () -> app.model().repoView().files().commitFiles().map(CommitFilesView::of)));

            // Escape in the commits lets go: the branches take the files pane once they have the focus again.
            SettledPilot.focus(testRunner, CommitsPanel.ID, app.drawnAt::size);
            SettledPilot.of(testRunner, app.drawnAt::size).press(KeyCode.ESCAPE);
            awaitModel(
                    testRunner, app, model -> model.repoView().files().locked().isEmpty());
            SettledPilot.focus(testRunner, BranchesPanel.ID, app.drawnAt::size);
            awaitModel(
                    testRunner,
                    app,
                    model -> !model.repoView()
                            .files()
                            .commitFiles()
                            .map(CommitFilesView::of)
                            .contains(opened));
        } finally {
            app.onStop();
        }
    }

    @Test
    void backToTheListChosenFromTheHelpGoesBackAsEscapeDoes() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, TestApp.SIZE, tickless())) {
            app.start(testRunner);
            awaitModel(
                    testRunner,
                    app,
                    model -> model.repoView().preview().commitDetail().isDefined());
            SettledPilot.focus(testRunner, CommitsPanel.ID, app.drawnAt::size);
            SettledPilot.of(testRunner, app.drawnAt::size).press(KeyCode.ENTER);
            await(
                    () -> FilesPanel.ID.equals(onRenderThread(testRunner, () -> focusedId(testRunner))),
                    "the files focused");

            // Escape is the first key of the files of a commit the help lists.
            int escape = Keymap.standard()
                    .bindings(Scopes.COMMIT_FILES.id())
                    .indexWhere(binding -> binding.key().equals(Keymap.ESCAPE));
            Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);
            pilot.press('?');
            for (int i = 0; i < escape; i++) {
                pilot.press(KeyCode.DOWN);
            }
            pilot.press(KeyCode.ENTER);

            await(
                    () -> CommitsPanel.ID.equals(onRenderThread(testRunner, () -> focusedId(testRunner))),
                    "the commits focused again");
            awaitModel(
                    testRunner,
                    app,
                    model -> model.repoView().files().commitFiles().exists(CommitFilesView::followed));
        } finally {
            app.onStop();
        }
    }

    @Test
    void theWindowIsTitledAfterTheRepository() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, TestApp.SIZE, tickless())) {
            app.start(testRunner);

            TestBackend backend = (TestBackend) testRunner.runner().tuiRunner().backend();
            await(
                    () -> onRenderThread(testRunner, backend::rawOutput).contains("\u001b]2;jazygit - jazygit\u0007"),
                    "the window was titled");
        } finally {
            app.onStop();
        }
    }

    @Test
    void theRepositoryTheRemotesAndGitHubAreEachReadEverySoOftenAndTheTimeIsNot() {
        assertEquals(
                List.of(
                        Tuple.of(Timers.REFRESH_INTERVAL, TimerMsg.Tick.class),
                        Tuple.of(Duration.ofMinutes(1), FetchMsg.Tick.class),
                        Tuple.of(Duration.ofMinutes(1), PrOverviewMsg.Tick.class),
                        Tuple.of(Duration.ofSeconds(10), PullRequestMsg.Tick.class),
                        Tuple.of(Duration.ofSeconds(10), ChecksMsg.Tick.class),
                        Tuple.of(Duration.ofSeconds(2), PullRequestMsg.MergeTick.class),
                        Tuple.of(Duration.ofSeconds(2), StackMsg.Tick.class)),
                Timers.ALL.map(
                        timer -> Tuple.of(timer.interval(), timer.msg().get().getClass())));
    }

    @Test
    void theStatusIsReadAgainWithoutInput() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        TestApp app = new TestApp(new Env(new CountsStatus(reads).git(), StubGitHub.of()));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, TestApp.SIZE, tickless())) {
            app.start(testRunner);
            int started = reads.get();

            await(() -> reads.get() >= started + 2, "the status was read again");
        } finally {
            app.onStop();
        }
    }

    @Test
    void tabMovesTheFocusBetweenTheFieldsOfAnOpenPopup() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, TestApp.SIZE, tickless())) {
            app.start(testRunner);
            SettledPilot.focus(testRunner, FilesPanel.ID, app.drawnAt::size);
            Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);

            pilot.press('c');
            awaitModel(testRunner, app, model -> model.popup(CommitPopup.class).isDefined());
            pilot.pause();
            assertEquals(CommitDialog.ID, onRenderThread(testRunner, () -> focusedId(testRunner)));

            pilot.press(KeyCode.TAB);
            // The frame the key draws, and the next ones, keep the focus where the key moved it.
            pilot.pause();
            pilot.pause();

            assertEquals(CommitDialog.DESCRIPTION_ID, onRenderThread(testRunner, () -> focusedId(testRunner)));
            assertEquals(CommitDialog.DESCRIPTION_ID, onRenderThread(testRunner, () -> app.drawnWith));
        } finally {
            app.onStop();
        }
    }

    @Test
    void qAndCtrlCDoNotQuitFromAListDialog() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, TestApp.SIZE, tickless())) {
            app.start(testRunner);
            SettledPilot.focus(testRunner, FilesPanel.ID, app.drawnAt::size);
            Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);
            // The recent repositories, a list dialog.
            pilot.press('r', KeyModifiers.CTRL);
            awaitModel(testRunner, app, model -> model.popup().isDefined());
            pilot.pause();

            pilot.press('q');
            pilot.press('Q');
            pilot.press('c', KeyModifiers.CTRL);
            pilot.pause();

            assertEquals(0, app.quits.get());
        } finally {
            app.onStop();
        }
    }

    @Test
    void ctrlCInTheCommitDialogKeepsTheTypedMessage() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, TestApp.SIZE, tickless())) {
            app.start(testRunner);
            SettledPilot.focus(testRunner, FilesPanel.ID, app.drawnAt::size);
            Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);
            pilot.press('c');
            awaitModel(testRunner, app, model -> model.popup(CommitPopup.class).isDefined());
            pilot.pause();
            pilot.press('h');
            pilot.press('i');
            awaitModel(
                    testRunner,
                    app,
                    model -> model.popup(CommitPopup.class)
                            .exists(popup -> popup.message().summary().equals("hi")));

            pilot.press('c', KeyModifiers.CTRL);
            pilot.pause();

            assertEquals(0, app.quits.get());
            assertEquals(
                    "hi",
                    onRenderThread(
                            testRunner,
                            () -> app.model()
                                    .popup(CommitPopup.class)
                                    .get()
                                    .message()
                                    .summary()));
        } finally {
            app.onStop();
        }
    }

    @Test
    void theGlobalKeysDoNothingWhileAPopupIsOpen() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, TestApp.SIZE, tickless())) {
            app.start(testRunner);
            SettledPilot.focus(testRunner, FilesPanel.ID, app.drawnAt::size);
            Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);
            pilot.press('c');
            awaitModel(testRunner, app, model -> model.popup(CommitPopup.class).isDefined());
            pilot.pause();

            pilot.press('M');
            pilot.press('p', KeyModifiers.CTRL);
            pilot.pause();

            assertEquals(
                    Screen.REPOSITORY,
                    onRenderThread(testRunner, () -> app.model().screen()));
            assertEquals(
                    true,
                    onRenderThread(
                            testRunner,
                            () -> app.model().popup(CommitPopup.class).isDefined()));
        } finally {
            app.onStop();
        }
    }

    @Test
    void qInTheHelpDialogStillQuits() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, TestApp.SIZE, tickless())) {
            app.start(testRunner);
            SettledPilot.focus(testRunner, FilesPanel.ID, app.drawnAt::size);
            Pilot pilot = SettledPilot.of(testRunner, app.drawnAt::size);
            pilot.press('?');
            awaitModel(testRunner, app, model -> model.popup(HelpPopup.class).isDefined());
            pilot.pause();

            pilot.press('q');

            await(() -> app.quits.get() == 1, "the app quit");
        } finally {
            app.onStop();
        }
    }

    @Test
    void qQuitsWhenNoPopupIsOpen() throws Exception {
        TestApp app = new TestApp();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(app::frame, TestApp.SIZE, tickless())) {
            app.start(testRunner);
            SettledPilot.focus(testRunner, FilesPanel.ID, app.drawnAt::size);

            SettledPilot.of(testRunner, app.drawnAt::size).press('q');

            await(() -> app.quits.get() == 1, "the app quit");
        } finally {
            app.onStop();
        }
    }

    /**
     * A repository that counts how often its status is read.
     */
    private record CountsStatus(AtomicInteger reads) {

        public Status status() {
            return new StubStatus() {
                @Override
                public Either<GitError, RepoStatus> read() {
                    reads.incrementAndGet();
                    return StubStatus.super.read();
                }
            };
        }

        Git git() {
            return StubGit.of().withStatus(status());
        }
    }
}
