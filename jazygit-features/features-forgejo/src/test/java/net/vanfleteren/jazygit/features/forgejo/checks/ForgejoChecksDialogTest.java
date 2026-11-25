package net.vanfleteren.jazygit.features.forgejo.checks;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static net.vanfleteren.jazygit.features.forgejo.checks.ChecksFixtures.LOADING;
import static net.vanfleteren.jazygit.features.forgejo.checks.ChecksFixtures.NOW;
import static net.vanfleteren.jazygit.features.forgejo.checks.ChecksFixtures.job;
import static net.vanfleteren.jazygit.features.forgejo.checks.ChecksFixtures.queued;
import static net.vanfleteren.jazygit.features.forgejo.checks.ChecksFixtures.read;
import static net.vanfleteren.jazygit.features.forgejo.checks.ChecksFixtures.status;
import static net.vanfleteren.jazygit.kernel.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import net.vanfleteren.jazygit.kernel.ui.text.TimeText;
import org.junit.jupiter.api.Test;

/**
 * The popup listing the checks on Forgejo one by one, in the frame GitHub's are listed in (ADR 195).
 */
class ForgejoChecksDialogTest {

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final ForgejoChecksDialog dialog = new ForgejoChecksDialog(
            dispatched::add, Clock.fixed(NOW, ZoneOffset.UTC), () -> ChecksAction.KEYS.apply(Keymap.empty()));

    @Test
    void whileFirstReadItSaysSoWithASpinner() throws Exception {
        String screen = screen(LOADING);

        assertTrue(screen.contains("Checks of feature"), screen);
        assertTrue(screen.contains("Reading the checks from Forgejo… " + TimeText.spinnerFrame(NOW)), screen);
        assertTrue(ForgejoChecksDialog.showsSpinner(TestModels.loaded().openPopup(LOADING)));
    }

    @Test
    void listsEveryCheckTheFailedOnesFirstWithHowLongItTookOrRuns() throws Exception {
        String screen = screen(read(List.of(
                job("ci / build (push)", CommitStatus.State.PASSED, 12, 0, 300, Option.of(60)),
                job("ci / lint (push)", CommitStatus.State.FAILED, 12, 1, 300, Option.of(90)),
                job("ci / test (push)", CommitStatus.State.RUNNING, 12, 2, 125, Option.none()),
                queued("ci / deploy (push)", 12, 3, 40),
                status("woodpecker", CommitStatus.State.PASSED, 600))));

        assertTrue(screen.contains("✗ 1 failed · ● 2 running · ✓ 2 passed"), screen);
        assertTrue(screen.contains("started 11:50:00"), screen);
        assertTrue(screen.contains("running 10m"), screen);
        assertTrue(screen.contains("60%"), screen);
        List<String> lines = List.of(screen.split("\n"));
        int lint = lines.indexWhere(line -> line.contains("ci / lint"));
        int test = lines.indexWhere(line -> line.contains("ci / test"));
        int deploy = lines.indexWhere(line -> line.contains("ci / deploy"));
        int build = lines.indexWhere(line -> line.contains("ci / build"));
        assertTrue(lint < test && test < deploy && deploy < build, screen);
        assertTrue(lines.get(lint).contains("▸ ✗ ci / lint (push)"), screen);
        assertTrue(lines.get(lint).contains("failed after 1m 30s"), screen);
        assertTrue(lines.get(test).contains("running 2m 5s"), screen);
        assertTrue(lines.get(deploy).contains("◌ ci / deploy (push)"), screen);
        assertTrue(lines.get(deploy).contains("queued 40s"), screen);
        assertTrue(lines.get(build).contains("1m"), screen);
    }

    @Test
    void onceAllAreDoneItSaysWhenTheyEndedAndHowLongTheyTook() throws Exception {
        String screen = screen(read(List.of(
                job("ci / build (push)", CommitStatus.State.PASSED, 12, 0, 300, Option.of(60)),
                job("ci / test (push)", CommitStatus.State.PASSED, 12, 1, 200, Option.of(100)))));

        assertTrue(screen.contains("started 11:55:00 · ended 11:58:20 · took 3m 20s"), screen);
        assertTrue(screen.contains("100%"), screen);
    }

    @Test
    void withoutChecksItSaysSo() throws Exception {
        assertTrue(screen(read(List.empty())).contains("No checks"));
    }

    @Test
    void aReadThatFailsAfterOneThatDidNotMarksTheChecksStale() throws Exception {
        String screen =
                screen(read(List.of(job("ci / build (push)", CommitStatus.State.RUNNING, 12, 0, 30, Option.none())))
                        .readingAgain(Reading.BY_ITSELF, ChecksFixtures.TIP, NOW)
                        .with(Either.left(refused("offline"))));

        assertTrue(screen.contains("Checks of feature · not up to date"), screen);
        assertTrue(screen.contains("ci / build"), screen);
    }

    @Test
    void readAgainAsAskedTheTitleShowsASpinner() throws Exception {
        String screen =
                screen(read(List.of(job("ci / build (push)", CommitStatus.State.RUNNING, 12, 0, 30, Option.none())))
                        .readingAgain(Reading.ASKED, ChecksFixtures.TIP, NOW));

        assertTrue(screen.contains("Checks of feature " + TimeText.spinnerFrame(NOW)), screen);
    }

    @Test
    void whileACheckRunsWhatItShowsMovesOnEverySecond() {
        Model running = TestModels.loaded()
                .withNow(NOW)
                .openPopup(read(List.of(job("b", CommitStatus.State.RUNNING, 12, 0, 30, Option.none()))));
        Model done = TestModels.loaded()
                .withNow(NOW)
                .openPopup(read(List.of(job("b", CommitStatus.State.PASSED, 12, 0, 30, Option.of(10)))));

        assertEquals(Option.of(NOW.plusSeconds(1)), ShownTime.next(ForgejoChecksDialog.shownTimes(running), NOW));
        assertEquals(List.empty(), ForgejoChecksDialog.shownTimes(done));
    }

    @Test
    void itsKeysSayWhatToDo() throws Exception {
        Model model = TestModels.loaded()
                .withNow(NOW)
                .openPopup(read(List.of(job("b", CommitStatus.State.FAILED, 12, 0, 30, Option.of(10)))));
        try (ToolkitTestRunner runner = start(model)) {
            SettledPilot.of(runner).press('n');
            SettledPilot.of(runner).press('e');
            SettledPilot.of(runner).press('r');
            SettledPilot.of(runner).press('R');
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
        }

        assertEquals(
                List.of(
                        new ChecksMsg.Stepped(1),
                        new ChecksMsg.LogRequested(),
                        new ChecksMsg.RerunRequested(),
                        new ChecksMsg.RefreshChosen(),
                        new ChecksMsg.Closed()),
                List.ofAll(dispatched));
    }

    @Test
    void whatFailedIsAFailedBuildThatLinksToItsLogs() {
        Model model = TestModels.loaded()
                .withNow(NOW)
                .openPopup(read(List.of(
                        job("ci / lint (push)", CommitStatus.State.FAILED, 12, 1, 30, Option.of(10)),
                        job("ci / build (push)", CommitStatus.State.PASSED, 12, 0, 30, Option.of(10)))));

        FailedBuild failed = ForgejoChecksDialog.failedBuild(
                        model.popup(ForgejoChecksPopup.class).get(), model)
                .get();

        assertEquals(Option.of("cvf/nix"), failed.repository());
        assertEquals(List.of("ci / lint (push)"), failed.jobs().map(FailedBuild.Job::name));
        assertTrue(failed.logs().contains("https://code.example.net/cvf/nix/actions/runs/12/jobs/1"));
        assertFalse(failed.running());
    }

    private String screen(ForgejoChecksPopup popup) throws Exception {
        return screen(TestModels.loaded().withNow(NOW).openPopup(popup));
    }

    private String screen(Model model) throws Exception {
        try (ToolkitTestRunner runner = start(model)) {
            return RenderedText.ofSize(runner, () -> view(model), 120, 50);
        }
    }

    private ToolkitTestRunner start(Model model) throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(model));
        SettledPilot.focus(runner, ForgejoChecksDialog.ID);
        RenderedText.of(runner, () -> view(model));
        return runner;
    }

    private Element view(Model model) {
        return stack(text("background"), dialog.render(model).get());
    }
}
