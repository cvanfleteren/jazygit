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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.ListCursor;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.PendingChecks;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.ui.CheckRows;
import net.vanfleteren.jazygit.ui.CheckTimesText;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.TextWidth;
import net.vanfleteren.jazygit.ui.Theme;
import org.junit.jupiter.api.Test;

class ChecksDialogTest {

    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");
    private static final RemoteBranch MAIN = new RemoteBranch("origin", new BranchName("main"));
    private static final ChecksPopup LOADING = ChecksPopup.loading(new BranchRef.Local(new BranchName("main")), MAIN);

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
        return LOADING.with(Either.right(new PendingChecks("owner/repo", HashMap.of(new BranchName("main"), checks))));
    }

    @Test
    void listsEveryCheckWithHowLongItTookOrRuns() throws Exception {
        String screen = screen(read(List.of(
                check("package", Checks.PASSING, 300, Option.of(60)),
                check("lint", Checks.FAILING, 300, Option.of(90)),
                check("verify", Checks.PENDING, 150, Option.none()),
                new Check("ci/status", Checks.PASSING))));

        assertTrue(screen.contains("Checks of main"), screen);
        assertTrue(screen.contains("❌ 1 failed · ⏳ 1 running · ✅ 2 passed · done in ?"), screen);
        // The names on the left, how each is doing or how long it took on the right.
        assertTrue(screen.contains("❌ lint" + " ".repeat(52) + "failed after 1m 30s│"), screen);
        assertTrue(screen.contains("⏳ verify" + " ".repeat(46) + "running 2m 30s · ? left│"), screen);
        assertTrue(screen.contains("✅ package" + " ".repeat(66) + "1m│"), screen);
        assertTrue(screen.contains("✅ ci/status"), screen);
        // Failed, running, then passed.
        assertTrue(screen.indexOf("lint") < screen.indexOf("verify"), screen);
        assertTrue(screen.indexOf("verify") < screen.indexOf("package"), screen);
    }

    @Test
    void aLongNameIsCutToLeaveRoomForTheTimingAtTheBorder() throws Exception {
        String longName = "x".repeat(80);
        String screen = screen(read(List.of(check(longName, Checks.PASSING, 300, Option.of(60)))));

        // 78 columns: the symbol takes 2, a space 1, the name 71 of which the last is the ellipsis, then 2 and "1m".
        assertFalse(screen.contains("x".repeat(71)), screen);
        assertTrue(screen.contains("✅ " + "x".repeat(70) + "…  1m│"), screen);
    }

    @Test
    void aLongNameOfACheckNotTimedIsCutAtTheBorder() throws Exception {
        String longName = "y".repeat(90);
        String screen = screen(read(List.of(new Check(longName, Checks.PASSING))));

        assertFalse(screen.contains(longName), screen);
        // 78 columns: the symbol takes 2, a space 1, so the name keeps 75 of which the last is the ellipsis.
        assertTrue(screen.contains("✅ " + "y".repeat(74) + "…│"), screen);
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

        // 78 columns: the symbol takes 2, a space 1, then the name 57 of which the last is the ellipsis, 2, and the
        // timings 16, as wide as "failed after 10m" also when it is not shown.
        String checkb = "checkb" + "z".repeat(50) + "…" + " ".repeat(2 + 14) + "1m│";
        assertTrue(top.contains("checka" + " ".repeat(53) + "failed after 10m│"), top);
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
        assertEquals(widest, CheckRows.doingWidth(running, running, Option.none(), NOW));
        assertEquals(widest, CheckRows.doingWidth(running, running, Option.none(), NOW.plusSeconds(5)));
        assertEquals(TextWidth.of("1m"), CheckRows.doingWidth(done, done, Option.none(), NOW));
    }

    @Test
    void aQueuedCheckIsDimmedAllAlongARunningOneOnlyInItsTime() {
        List<Check> all = List.of(queued("acceptance"), check("verify", Checks.PENDING, 150, Option.none()));

        // After the symbol, the name and how it is doing.
        List<StyledElement<?>> queued =
                CheckRows.filling(all.head(), all, Option.none(), NOW, 78, 20).tail();
        List<StyledElement<?>> running =
                CheckRows.filling(all.last(), all, Option.none(), NOW, 78, 20).tail();

        assertTrue(queued.forAll(piece -> piece.cssClasses().contains(Theme.Classes.DIM)));
        assertFalse(running.head().cssClasses().contains(Theme.Classes.DIM));
        assertTrue(running.last().cssClasses().contains(Theme.Classes.DIM));
    }

    @Test
    void manyChecksScrollFromTheLineOfTheListCursor() throws Exception {
        Model model = TestModels.loaded().withNow(NOW).openPopup(many());

        String top = screen(model);
        assertTrue(top.contains("checka"), top);
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
