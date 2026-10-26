package net.vanfleteren.jazygit;

import static net.vanfleteren.jazygit.TestApp.await;
import static net.vanfleteren.jazygit.TestApp.awaitModel;
import static net.vanfleteren.jazygit.TestApp.focusedId;
import static net.vanfleteren.jazygit.TestApp.onRenderThread;
import static net.vanfleteren.jazygit.TestApp.tickless;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.pilot.Pilot;
import io.vavr.Tuple;
import io.vavr.collection.List;
import io.vavr.control.Either;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import net.vanfleteren.jazygit.feature.commit.CommitDialog;
import net.vanfleteren.jazygit.feature.commit.create.CommitPopup;
import net.vanfleteren.jazygit.feature.fetch.FetchMsg;
import net.vanfleteren.jazygit.feature.github.checks.ChecksMsg;
import net.vanfleteren.jazygit.feature.github.show.PullRequestMsg;
import net.vanfleteren.jazygit.feature.help.HelpPopup;
import net.vanfleteren.jazygit.gh.StubGitHub;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Screen;
import net.vanfleteren.jazygit.state.prs.PrOverviewMsg;
import net.vanfleteren.jazygit.ui.CommitsPanel;
import net.vanfleteren.jazygit.ui.FilesPanel;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.TimeText;
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
    void theTimeTheRepositoryTheRemotesAndGitHubAreEachReadEverySoOften() {
        assertEquals(
                List.of(
                        Tuple.of(TimeText.CLOCK_INTERVAL, Msg.Clock.class),
                        Tuple.of(JazygitApp.REFRESH_INTERVAL, Msg.Tick.class),
                        Tuple.of(Duration.ofMinutes(1), FetchMsg.Tick.class),
                        Tuple.of(Duration.ofMinutes(1), PrOverviewMsg.Tick.class),
                        Tuple.of(Duration.ofSeconds(10), PullRequestMsg.Tick.class),
                        Tuple.of(Duration.ofSeconds(10), ChecksMsg.Tick.class),
                        Tuple.of(Duration.ofSeconds(2), PullRequestMsg.MergeTick.class)),
                JazygitApp.TIMERS.map(
                        timer -> Tuple.of(timer.interval(), timer.msg().get().getClass())));
    }

    @Test
    void theStatusIsReadAgainWithoutInput() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        TestApp app = new TestApp(new Env(new CountsStatus(reads), StubGitHub.empty()));
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
    private record CountsStatus(AtomicInteger reads) implements StubGitProvider {

        @Override
        public Either<ProcessError, RepoStatus> status() {
            reads.incrementAndGet();
            return StubGitProvider.super.status();
        }
    }
}
