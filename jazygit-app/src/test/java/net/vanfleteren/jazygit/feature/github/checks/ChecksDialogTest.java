package net.vanfleteren.jazygit.feature.github.checks;

import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;
import static net.vanfleteren.jazygit.state.TestModels.refused;
import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
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
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
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
        assertTrue(screen.contains("❌ lint       failed after 1m"), screen);
        assertTrue(screen.contains("⏳ verify     running 2m · ? left"), screen);
        assertTrue(screen.contains("✅ package    1m"), screen);
        assertTrue(screen.contains("✅ ci/status"), screen);
        // Failed, running, then passed.
        assertTrue(screen.indexOf("lint") < screen.indexOf("verify"), screen);
        assertTrue(screen.indexOf("verify") < screen.indexOf("package"), screen);
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
