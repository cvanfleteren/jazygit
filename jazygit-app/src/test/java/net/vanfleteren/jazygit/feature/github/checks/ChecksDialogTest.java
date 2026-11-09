package net.vanfleteren.jazygit.feature.github.checks;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static net.vanfleteren.jazygit.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.timing.CheckHistory;
import net.vanfleteren.jazygit.gh.timing.CheckSample;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.CheckTimes;
import net.vanfleteren.jazygit.state.CheckTimesMsg;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.ListCursor;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.PendingChecks;
import net.vanfleteren.jazygit.state.Reading;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.CheckRows;
import net.vanfleteren.jazygit.ui.CheckTimesText;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.TextWidth;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.TimeText;
import org.junit.jupiter.api.Test;

class ChecksDialogTest {

    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");
    private static final RemoteBranch MAIN = new RemoteBranch("origin", new BranchName("main"));
    private static final ChecksPopup LOADING =
            ChecksPopup.loading(new BranchRef.Local(new BranchName("main")), MAIN, Instant.EPOCH);

    private final java.util.List<Msg> dispatched = new ArrayList<>();
    private final ChecksDialog dialog = new ChecksDialog(dispatched::add, Clock.fixed(NOW, ZoneOffset.UTC));

    /**
     * A check of workflow {@code Build}: running since {@code started} seconds before NOW without {@code took},
     * else done after {@code took} seconds.
     */
    private static Check check(String name, Checks state, int started, Option<Integer> took) {
        return new Check(
                name,
                state,
                Option.of(new CheckRun(
                        name.hashCode(),
                        "Build",
                        state == Checks.PASSING ? "SUCCESS" : state == Checks.FAILING ? "FAILURE" : "",
                        Option.of(NOW.minusSeconds(started)),
                        took.map(seconds -> NOW.minusSeconds(started - seconds)),
                        1,
                        NOW.minusSeconds(started + 2),
                        1,
                        "push")));
    }

    /**
     * A check of workflow {@code Build} run again, its second attempt, running since {@code started} seconds before
     * NOW, of a run created long before.
     */
    private static Check rerun(String name, int started) {
        return new Check(
                name,
                Checks.PENDING,
                Option.of(new CheckRun(
                        name.hashCode(),
                        "Build",
                        "",
                        Option.of(NOW.minusSeconds(started)),
                        Option.none(),
                        2,
                        NOW.minusSeconds(86_400),
                        2,
                        "push")));
    }

    /**
     * A check of workflow {@code Build} that waits to be started.
     */
    private static Check queued(String name) {
        return new Check(
                name,
                Checks.PENDING,
                Option.of(new CheckRun(
                        name.hashCode(),
                        "Build",
                        "",
                        Option.none(),
                        Option.none(),
                        1,
                        NOW.minusSeconds(10),
                        1,
                        "push")));
    }

    private static ChecksPopup read(List<Check> checks) {
        return LOADING.with(readOf(checks));
    }

    private static Either<Failure, PendingChecks> readOf(List<Check> checks) {
        return Either.right(new PendingChecks("owner/repo", HashMap.of(new BranchName("main"), checks)));
    }

    @Test
    void whileReadAgainAsAskedTheTitleShowsASpinner() throws Exception {
        ChecksPopup shown = read(List.of(check("verify", Checks.PENDING, 30, Option.none())));

        assertTrue(screen(shown.readingAgain(Reading.ASKED, NOW))
                .contains("Checks of main " + TimeText.spinnerFrame(NOW)));
    }

    @Test
    void whileReadAgainByItselfTheTitleShowsNoSpinner() throws Exception {
        String screen = screen(read(List.of(check("verify", Checks.PENDING, 30, Option.none())))
                .readingAgain(Reading.BY_ITSELF, NOW));

        assertTrue(screen.contains("Checks of main"), screen);
        assertFalse(screen.contains("Checks of main " + TimeText.spinnerFrame(NOW)), screen);
    }

    @Test
    void aReadByItselfThatFailsMarksTheChecksStale() throws Exception {
        String screen = screen(read(List.of(check("verify", Checks.PENDING, 30, Option.none())))
                .readingAgain(Reading.BY_ITSELF, NOW)
                .with(Either.left(refused("offline"))));

        assertTrue(screen.contains("Checks of main · not up to date"), screen);
    }

    @Test
    void waitsForTheChecksThatDoNotShowYetWithoutListingThem() throws Exception {
        // package runs 60s; acceptance waits for it, so GitHub makes it once package is done, 254s into its run.
        List<CheckSample> samples = List.range(0, 3)
                .flatMap(i -> List.of(
                        new CheckSample(
                                "Build/package", i, Duration.ofSeconds(60), Duration.ofSeconds(60), "push", NOW),
                        new CheckSample(
                                "Build/acceptance",
                                10 + i,
                                Duration.ofSeconds(190),
                                Duration.ofSeconds(254),
                                "push",
                                NOW)));
        CheckTimes times = CheckTimes.of("owner/repo", CheckHistory.EMPTY.with(samples, NOW), Option.none());

        String screen = screen(TestModels.loaded()
                .withNow(NOW)
                .mapGitHub(gitHub -> gitHub.withCheckTimes(Option.of(times)))
                .openPopup(read(List.of(check("package", Checks.PENDING, 30, Option.none())))));

        assertTrue(screen.contains("● 1 running · done in ~3m 40s"), screen);
        assertFalse(screen.contains("acceptance"), screen);
    }

    @Test
    void listsEveryCheckWithHowLongItTookOrRuns() throws Exception {
        String screen = screen(read(List.of(
                check("package", Checks.PASSING, 300, Option.of(60)),
                check("lint", Checks.FAILING, 300, Option.of(90)),
                check("verify", Checks.PENDING, 150, Option.none()),
                new Check("ci/status", Checks.PASSING))));

        assertTrue(screen.contains("Checks of main"), screen);
        assertTrue(screen.contains("✗ 1 failed · ● 1 running · ✓ 2 passed · done in ?"), screen);
        // While one is not done: the names on the left, then a bar of 24 columns, dashed when how far it is is not
        // known, then how long each runs of how long it is expected to take, or how long it took, on the right.
        assertTrue(screen.contains("✗ lint" + " ".repeat(73) + "failed after 1m 30s│"), screen);
        assertTrue(
                screen.contains("● verify" + " ".repeat(45) + "╌".repeat(24) + " ".repeat(13) + "2m 30s/?│"), screen);
        assertTrue(screen.contains("✓ package" + " ".repeat(87) + "1m│"), screen);
        assertTrue(screen.contains("✓ ci/status"), screen);
        // Failed, running, then passed.
        assertTrue(screen.indexOf("lint") < screen.indexOf("verify"), screen);
        assertTrue(screen.indexOf("verify") < screen.indexOf("package"), screen);
    }

    @Test
    void saysWhenTheChecksStartedAndHowLongTheyRun() throws Exception {
        String screen = screen(read(List.of(
                check("package", Checks.PASSING, 300, Option.of(60)),
                check("verify", Checks.PENDING, 150, Option.none()))));

        // From when the first run was created, 302s ago, to the second.
        assertTrue(screen.contains("│started 11:54:58 · running 5m 2s "), screen);
        assertFalse(screen.contains("ended"), screen);
    }

    @Test
    void saysWhenTheChecksStartedAndEndedOnceAllAreDoneAFailedOneToo() throws Exception {
        String screen = screen(read(List.of(
                check("package", Checks.PASSING, 300, Option.of(60)),
                check("lint", Checks.FAILING, 300, Option.of(90)),
                new Check("ci/status", Checks.PASSING))));

        assertTrue(screen.contains("│started 11:54:58 · ended 11:56:30 · took 1m 32s "), screen);
    }

    @Test
    void aTimeOfAnotherDayHasItsDate() throws Exception {
        String screen = screen(read(List.of(check("package", Checks.PASSING, 86_400 + 100, Option.of(60)))));

        assertTrue(screen.contains("started 2026-10-24 11:58:18 · ended 2026-10-24 11:59:20 · took 1m 2s"), screen);
    }

    @Test
    void commitStatusesAloneShowNoStartNorEnd() throws Exception {
        String screen = screen(read(List.of(new Check("ci/status", Checks.PASSING))));

        assertTrue(screen.contains("ci/status"), screen);
        assertFalse(screen.contains("started"), screen);
    }

    @Test
    void checksThatFailedToBeReadAgainKeepWhenTheyStarted() throws Exception {
        String screen = screen(read(List.of(check("verify", Checks.PENDING, 150, Option.none())))
                .with(Either.left(refused("offline"))));

        assertTrue(screen.contains("Checks of main · not up to date"), screen);
        assertTrue(screen.contains("started 11:57:28 · running 2m 32s"), screen);
    }

    @Test
    void aLongNameIsCutToLeaveRoomForTheTimingAtTheBorder() throws Exception {
        String longName = "x".repeat(100);
        String screen = screen(read(List.of(check(longName, Checks.PASSING, 300, Option.of(60)))));

        // 98 columns: the symbol takes 1, a space 1, the name 92 of which the last is the ellipsis, then 2 and "1m".
        assertFalse(screen.contains("x".repeat(92)), screen);
        assertTrue(screen.contains("✓ " + "x".repeat(91) + "…  1m│"), screen);
    }

    @Test
    void aLongNameOfACheckNotTimedIsCutAtTheBorder() throws Exception {
        String longName = "y".repeat(110);
        String screen = screen(read(List.of(new Check(longName, Checks.PASSING))));

        assertFalse(screen.contains(longName), screen);
        // 98 columns: the symbol takes 1, a space 1, so the name keeps 96 of which the last is the ellipsis.
        assertTrue(screen.contains("✓ " + "y".repeat(95) + "…│"), screen);
    }

    @Test
    void longNamesAreCutAlikeWhenTheWidestTimingIsScrolledOutOfView() throws Exception {
        // checka failed after 10m, the widest timing; the 16 others, with long names, passed after 1m.
        ChecksPopup popup = read(List.range(0, 17)
                .map(i -> i == 0
                        ? check("checka", Checks.FAILING, 900, Option.of(600))
                        : check("check" + (char) ('a' + i) + "z".repeat(80), Checks.PASSING, 300, Option.of(60))));
        Model model = TestModels.loaded().withNow(NOW).openPopup(popup);

        String top = screen(model);
        String scrolled = screen(model.withListCursor(ListCursor.START.at(1)));

        // 98 columns: the symbol takes 1, a space 1, then the name 78 of which the last is the ellipsis, 2, and the
        // timings 16, as wide as "failed after 10m" also when it is not shown.
        String checkb = "checkb" + "z".repeat(71) + "…" + " ".repeat(2 + 14) + "1m│";
        assertTrue(top.contains("checka" + " ".repeat(74) + "failed after 10m│"), top);
        assertTrue(top.contains(checkb), top);
        assertFalse(scrolled.contains("checka"), scrolled);
        assertTrue(scrolled.contains(checkb), scrolled);
    }

    @Test
    void whileACheckRunsTheTimingsKeepTheirWidthAsTheSecondsMoveOn() {
        // 7m 55s, then 8m: a narrower time, which would move the names.
        List<Check> running = List.of(check("verify", Checks.PENDING, 475, Option.none()));
        List<Check> done = List.of(check("verify", Checks.PASSING, 475, Option.of(60)));

        int widest = TextWidth.of(CheckTimesText.widestRunning());
        assertEquals(widest, CheckRows.doingWidth(running, running, Option.none(), NOW, true));
        assertEquals(widest, CheckRows.doingWidth(running, running, Option.none(), NOW.plusSeconds(5), true));
        assertEquals(TextWidth.of("1m"), CheckRows.doingWidth(done, done, Option.none(), NOW, true));
    }

    @Test
    void aQueuedCheckIsDimmedAllAlongARunningOneOnlyInItsTime() {
        List<Check> all = List.of(queued("acceptance"), check("verify", Checks.PENDING, 150, Option.none()));

        // After the symbol, the name and how it is doing.
        List<StyledElement<?>> queued = CheckRows.filling(all.head(), all, Option.none(), NOW, 78, 20, true)
                .tail();
        List<StyledElement<?>> running = CheckRows.filling(all.last(), all, Option.none(), NOW, 78, 20, true)
                .tail();

        assertTrue(queued.forAll(piece -> piece.cssClasses().contains(Theme.Classes.DIM)));
        assertFalse(running.head().cssClasses().contains(Theme.Classes.DIM));
        assertTrue(running.last().cssClasses().contains(Theme.Classes.DIM));
    }

    @Test
    void aBarShowsHowFarTheChecksAreTowardsWhenTheyAreExpectedAndEachRunningOneToo() throws Exception {
        // verify runs 150s of 300s: from its run, created 152s ago, 152s of 302s.
        String screen =
                screen(timed(300).openPopup(read(List.of(check("verify", Checks.PENDING, 150, Option.none())))));

        // 93 columns of bar: 95% of 152/302 of them, then how far that is.
        assertTrue(screen.contains("│" + "━".repeat(44) + "─".repeat(49) + "  47%│"), screen);
        // After the name, 24 columns: 95% of half of them; then how long it runs of the 300s it usually takes.
        assertTrue(
                screen.contains(
                        "● verify" + " ".repeat(49) + "━".repeat(11) + "─".repeat(13) + " ".repeat(7) + "2m 30s/~5m│"),
                screen);
    }

    @Test
    void theBarIsFullOnlyOnceTheChecksAreDone() throws Exception {
        List<Check> late = List.of(check("verify", Checks.PENDING, 3000, Option.none()));
        List<Check> done = List.of(check("verify", Checks.PASSING, 300, Option.of(290)));

        // Long past when it was expected, it is not full, and says how long they usually take.
        String lateScreen = screen(timed(300).openPopup(read(late)));
        assertTrue(lateScreen.contains("  98%│"), lateScreen);
        assertTrue(lateScreen.contains("● 1 running · taking longer than usual (~5m)"), lateScreen);
        assertTrue(screen(timed(300).openPopup(read(done))).contains("│" + "━".repeat(93) + " 100%│"));
    }

    @Test
    void theBarIsRedOnceOneFailedGreenOnceAllPassedYellowWhenLateAndDimmedWhenNotKnown() {
        CheckTimes times = times(300);
        List<Check> running = List.of(check("verify", Checks.PENDING, 150, Option.none()));
        List<Check> late = List.of(check("verify", Checks.PENDING, 600, Option.none()));
        List<Check> failed = running.append(check("lint", Checks.FAILING, 150, Option.of(30)));
        List<Check> passed = List.of(check("verify", Checks.PASSING, 300, Option.of(290)));

        assertEquals(Theme.Classes.CHECKS_PROGRESS, barClass(running, Option.of(times)));
        assertEquals(Theme.Classes.CHECKS_RUNNING, barClass(late, Option.of(times)));
        assertEquals(Theme.Classes.CHECKS_FAILED, barClass(failed, Option.of(times)));
        assertEquals(Theme.Classes.CHECKS_PASSED, barClass(passed, Option.of(times)));
        assertEquals(Theme.Classes.DIM, barClass(running, Option.none()));
    }

    @Test
    void theBarDoesNotGoBackWhenTheChecksAreExpectedLaterOnceReadAgain() {
        List<Check> checks = List.of(check("verify", Checks.PENDING, 150, Option.none()));
        Model before = timed(300);
        ChecksPopup popup = read(checks);
        double shown = popup.progress(before);
        // Read again, the checks are learned to take twice as long.
        Model after = timed(600);
        ChecksPopup again = popup.with(readOf(checks), before);

        assertTrue(popup.progress(after) < shown);
        assertEquals(shown, again.progress(after.withNow(NOW.plusSeconds(1))), 1e-9);
        // Checks run again start the bar again.
        ChecksPopup rerun = again.with(readOf(List.of(rerun("verify", 10))), after);
        assertTrue(rerun.progress(after) < shown);
    }

    @Test
    void aWorkflowRunAgainStartsTheBarAgainFromWhereItWasDoneOrLate() {
        Model model = timed(300);
        Check lint = check("lint", Checks.PASSING, 7200, Option.of(30));
        ChecksPopup done = read(List.of(lint, check("verify", Checks.PASSING, 7200, Option.of(290))));
        ChecksPopup late = read(List.of(lint, check("verify", Checks.PENDING, 3000, Option.none())));
        List<Check> again = List.of(lint, rerun("verify", 150));
        double fresh = read(again).progress(model);

        assertTrue(late.progress(model) > 0.98, String.valueOf(late.progress(model)));
        assertEquals(fresh, done.with(readOf(again), model).progress(model), 1e-9);
        assertEquals(fresh, late.with(readOf(again), model).progress(model), 1e-9);
        // From the start of the run again, not of lint two hours before.
        assertTrue(fresh < 0.5, String.valueOf(fresh));
    }

    @Test
    void commitStatusesOfAnotherCommitStartTheBarAgain() {
        Model model = TestModels.loaded().withNow(NOW);
        ChecksPopup old = read(List.of(
                new Check("ci/build", Checks.PASSING),
                new Check("ci/lint", Checks.PASSING),
                new Check("ci/test", Checks.PASSING),
                new Check("ci/deploy", Checks.PENDING)));
        List<Check> next = List.of(
                new Check("ci/build", Checks.PENDING),
                new Check("ci/lint", Checks.PENDING),
                new Check("ci/test", Checks.PENDING),
                new Check("ci/deploy", Checks.PENDING));

        assertEquals(0.95 * 3 / 4, old.progress(model), 1e-9);
        assertEquals(0, old.with(readOf(next), model).progress(model), 1e-9);
    }

    @Test
    void theBarDoesNotGoBackWhenHowLongTheChecksTakeIsLearnedWhileItIsOpen() {
        // 3 of 4 done, how long they take not known yet: 95% of 3/4.
        Model model = TestModels.loaded()
                .withNow(NOW)
                .openPopup(read(List.of(
                        check("package", Checks.PASSING, 300, Option.of(60)),
                        check("lint", Checks.PASSING, 300, Option.of(60)),
                        check("build", Checks.PASSING, 300, Option.of(60)),
                        check("verify", Checks.PENDING, 10, Option.none()))));
        double shown = model.popup(ChecksPopup.class).get().progress(model);

        Model learned = Update.update(
                        model,
                        new CheckTimesMsg.Recorded("owner/repo", times(300).history(), Option.none(), false))
                .model();

        assertEquals(0.95 * 3 / 4, shown, 1e-9);
        assertEquals(shown, learned.popup(ChecksPopup.class).get().progress(learned), 1e-9);
    }

    @Test
    void takingLongerThanUsualOfAWorkflowRunAgainSaysHowLongItUsuallyTakes() throws Exception {
        // verify, run again 600s ago, usually takes 302s from the start of its run; lint ran two hours before.
        String screen = screen(timed(300)
                .openPopup(read(List.of(check("lint", Checks.PASSING, 7200, Option.of(30)), rerun("verify", 600)))));

        assertTrue(screen.contains("taking longer than usual (~5m)"), screen);
    }

    @Test
    void manyChecksScrollFromTheLineOfTheListCursor() throws Exception {
        Model model = TestModels.loaded().withNow(NOW).openPopup(many());

        String top = screen(model);
        // 16 at once, under when they started and ended.
        assertTrue(top.contains("started"), top);
        assertTrue(top.contains("checka"), top);
        assertTrue(top.contains("checkp"), top);
        assertFalse(top.contains("checkq"), top);
        assertFalse(top.contains("checkt"), top);

        String bottom = screen(model.withListCursor(ListCursor.START.at(4)));
        assertFalse(bottom.contains("checka "), bottom);
        assertTrue(bottom.contains("checkt"), bottom);
        // Past the end, the last checks stay at the bottom.
        assertEquals(bottom, screen(model.withListCursor(ListCursor.START.at(10))));
    }

    @Test
    void theKeysScrollAsFarAsTheLastCheck() throws Exception {
        Model model = TestModels.loaded().withNow(NOW).openPopup(many());

        // 20 checks of which 16 are shown: they scroll 4 at most.
        assertEquals(List.of(1, 4, 0), scrolledTo(model, 'j', KeyCode.PAGE_DOWN, 'k'));
        assertEquals(
                List.of(4, 3, 4, 0),
                scrolledTo(
                        model.withListCursor(ListCursor.START.at(4)), KeyCode.DOWN, KeyCode.UP, 'j', KeyCode.PAGE_UP));
    }

    @Test
    void saysWhileTheChecksAreReadOrWhyThatFailed() throws Exception {
        assertTrue(screen(LOADING).contains("Reading the checks from GitHub…"));
        assertTrue(screen(LOADING.with(Either.left(refused("offline")))).contains("Could not read the checks"));
        assertTrue(screen(read(List.empty())).contains("No checks"));
    }

    @Test
    void checksThatFailedToBeReadAgainAreMarkedStale() throws Exception {
        String screen =
                screen(read(List.of(new Check("ci/status", Checks.PASSING))).with(Either.left(refused("offline"))));

        assertTrue(screen.contains("Checks of main · not up to date"), screen);
        assertTrue(screen.contains("ci/status"), screen);
    }

    @Test
    void refreshIsChosenWithCapitalRAndEscapeOrEnterCloses() throws Exception {
        Model model = TestModels.loaded().withNow(NOW).openPopup(read(List.empty()));
        try (ToolkitTestRunner runner = start(model)) {
            SettledPilot.of(runner).press('R');
            SettledPilot.of(runner).press(KeyCode.ESCAPE);
            SettledPilot.of(runner).press(KeyCode.ENTER);
            SettledPilot.of(runner).pause();
        }

        assertEquals(
                java.util.List.of(new ChecksMsg.RefreshChosen(), new ChecksMsg.Closed(), new ChecksMsg.Closed()),
                dispatched);
    }

    /**
     * What was learned of check {@code Build/verify}: it runs {@code seconds}, done 2s more after its run was created.
     */
    private static CheckTimes times(int seconds) {
        List<CheckSample> samples = List.range(0, 3)
                .map(i -> new CheckSample(
                        "Build/verify", i, Duration.ofSeconds(seconds), Duration.ofSeconds(seconds + 2), "push", NOW));
        return CheckTimes.of("owner/repo", CheckHistory.EMPTY.with(samples, NOW), Option.none());
    }

    private static Model timed(int seconds) {
        return TestModels.loaded().withNow(NOW).mapGitHub(gitHub -> gitHub.withCheckTimes(Option.of(times(seconds))));
    }

    /**
     * The class of the part done of the bar of how far {@code checks} are together, as {@code times} tell.
     */
    private static String barClass(List<Check> checks, Option<CheckTimes> times) {
        return ChecksDialog.gauge(checks, times, 0.5, NOW).head().cssClasses().stream()
                .findFirst()
                .orElseThrow();
    }

    private static ChecksPopup many() {
        return read(List.range(0, 20).map(i -> check("check" + (char) ('a' + i), Checks.PASSING, 300, Option.of(60))));
    }

    /**
     * The first check shown after each key in {@code keys}, each pressed on {@code model} as it is.
     */
    private List<Integer> scrolledTo(Model model, Object... keys) throws Exception {
        dispatched.clear();
        try (ToolkitTestRunner runner = start(model)) {
            for (Object key : keys) {
                if (key instanceof Character c) {
                    SettledPilot.of(runner).press(c);
                } else {
                    SettledPilot.of(runner).press((KeyCode) key);
                }
            }
            SettledPilot.of(runner).pause();
        }
        return List.ofAll(dispatched)
                .map(msg -> ((Msg.ListCursorChanged) msg).cursor().line().get());
    }

    private String screen(ChecksPopup popup) throws Exception {
        return screen(TestModels.loaded().withNow(NOW).openPopup(popup));
    }

    private String screen(Model model) throws Exception {
        try (ToolkitTestRunner runner = start(model)) {
            return RenderedText.ofSize(runner, () -> view(model), 120, 50);
        }
    }

    private ToolkitTestRunner start(Model model) throws Exception {
        ToolkitTestRunner runner = ToolkitTestRunner.runTest(() -> view(model));
        SettledPilot.focus(runner, ChecksDialog.ID);
        RenderedText.of(runner, () -> view(model));
        return runner;
    }

    private Element view(Model model) {
        return stack(text("background"), dialog.render(model).get());
    }
}
