package net.vanfleteren.jazygit.ui;

import static net.vanfleteren.jazygit.git.StubGit.executed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.tui.pilot.Pilot;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import net.vanfleteren.jazygit.branches.branch.push.PushCmd;
import net.vanfleteren.jazygit.changes.commit.create.CreateCommitCmd;
import net.vanfleteren.jazygit.changes.stage.StageCmd;
import net.vanfleteren.jazygit.github.show.PullRequestCmd;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.operation.LogEntry;
import net.vanfleteren.jazygit.state.operation.Notice;
import net.vanfleteren.jazygit.state.operation.Operation;
import net.vanfleteren.jazygit.state.preferences.Screen;
import net.vanfleteren.jazygit.ui.theme.Theme;
import org.junit.jupiter.api.Test;

/**
 * The command log panel shows why the last operation failed, or a notice about it.
 */
class CommandLogPanelTest {

    @Test
    void showsTheLastOperationError() throws Exception {
        Model model = TestModels.withCheckoutError(TestModels.loaded(SampleData.git()), "foo", "boom");

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> shown(model))) {
            String screen = RenderedText.of(testRunner, () -> shown(model));
            assertTrue(screen.contains("Command log"), screen);
            assertTrue(screen.contains("Checkout of foo failed: boom"), screen);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void showsANoticeWhenTheLastOperationStoppedOnConflicts() throws Exception {
        Model model = TestModels.loaded(SampleData.git()).inform(new Notice.StoppedOnConflicts(Option.none(), 2));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> shown(model))) {
            String screen = RenderedText.of(testRunner, () -> shown(model));
            assertTrue(screen.contains("The merge stopped on 2 conflicts"), screen);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void showsExecutedCommandsUnderTheirTitle() throws Exception {
        Model model = TestModels.withLog(
                TestModels.loaded(SampleData.git()), entry(PushCmd.PUSH, executed("git push origin main")));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> shown(model))) {
            String screen = RenderedText.of(testRunner, () -> shown(model));
            // The title after when the operation was done.
            assertTrue(screen.contains("14:32:05 Push"), screen);
            assertTrue(screen.contains("  git push origin main"), screen);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void keepsTheNewestLinesWhenTheyDoNotAllFit() throws Exception {
        Model model = TestModels.withLog(
                TestModels.loaded(SampleData.git()),
                entry(StageCmd.STAGE, executed("git add -- old.txt")),
                entry(
                        CreateCommitCmd.COMMIT,
                        executed(
                                "git commit -m a",
                                "git commit -m b",
                                "git commit -m c",
                                "git commit -m d",
                                "git commit -m e",
                                "git commit -m f",
                                "git commit -m g")));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> shown(model))) {
            String screen = RenderedText.ofLarge(testRunner, () -> shown(model));
            assertFalse(screen.contains("old.txt"), screen);
            assertTrue(screen.contains("git commit -m g"), screen);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void isItsBordersAndTitleAloneWhenEmpty() throws Exception {
        Model model = TestModels.loaded(SampleData.git());

        assertEquals(CommandLogPanel.BORDERS, CommandLogPanel.height(model, Option.none()));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> shown(model))) {
            String screen = RenderedText.of(testRunner, () -> shown(model));
            assertTrue(screen.contains("Command log"), screen);
            assertFalse(screen.contains("failed"), screen);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void shrinksToItsBordersWhileTheContentPanelIsMaximized() throws Exception {
        Model log = TestModels.withLog(
                TestModels.loaded(SampleData.git()), entry(PushCmd.PUSH, executed("git push origin main")));
        Model model = collapsed(log);

        assertEquals(4, CommandLogPanel.height(log, Option.none()));
        assertEquals(2, CommandLogPanel.height(model, Option.none()));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> shown(model))) {
            String screen = RenderedText.of(testRunner, () -> shown(model));
            assertTrue(screen.contains("Command log"), screen);
            assertFalse(screen.contains("git push origin main"), screen);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void keepsTheErrorWhileTheContentPanelIsMaximized() throws Exception {
        Model model = collapsed(TestModels.withCheckoutError(TestModels.loaded(SampleData.git()), "foo", "boom"));

        assertEquals(3, CommandLogPanel.height(model, Option.none()));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> shown(model))) {
            String screen = RenderedText.of(testRunner, () -> shown(model));
            assertTrue(screen.contains("Checkout of foo failed: boom"), screen);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void isAsTallAsItsLinesUpToTheMost() {
        Model one = TestModels.withLog(
                TestModels.loaded(SampleData.git()), entry(PushCmd.PUSH, executed("git push origin main")));
        Model many = TestModels.withLog(
                TestModels.loaded(SampleData.git()),
                entry(StageCmd.STAGE, executed("git add -- a", "git add -- b", "git add -- c")),
                entry(StageCmd.STAGE, executed("git add -- d", "git add -- e", "git add -- f")));

        // The title of the operation and its command.
        assertEquals(2 + 2, CommandLogPanel.height(one, Option.none()));
        assertEquals(2 + CommandLogPanel.VISIBLE_LINES, CommandLogPanel.height(many, Option.none()));
    }

    @Test
    void takesOnlyTheRowsTheContentPanelSpares() {
        Model model = TestModels.withLog(
                TestModels.loaded(SampleData.git()),
                entry(StageCmd.STAGE, executed("git add -- a", "git add -- b", "git add -- c")),
                entry(StageCmd.STAGE, executed("git add -- d", "git add -- e", "git add -- f")));

        assertEquals(10, CommandLogPanel.height(model, room(CommandLogPanel.CONTENT_MIN_ROWS + 20)));
        assertEquals(5, CommandLogPanel.height(model, room(CommandLogPanel.CONTENT_MIN_ROWS + 5)));
        // Never fewer than its borders, which show its title, without an error to keep a row for.
        assertEquals(2, CommandLogPanel.height(model, room(CommandLogPanel.CONTENT_MIN_ROWS + 1)));
        assertEquals(2, CommandLogPanel.height(model, room(5)));
    }

    @Test
    void showsTheNewestLinesAndTheErrorInTheRowsItTakes() throws Exception {
        Model model = TestModels.withCheckoutError(
                TestModels.withLog(
                        TestModels.loaded(SampleData.git()),
                        entry(StageCmd.STAGE, executed("git add -- old.txt", "git add -- new.txt"))),
                "foo",
                "boom");

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(
                () -> CommandLogPanel.fitted(model, 4, Option.none(), Theme.SpanStyles.NONE, ZoneOffset.UTC))) {
            String screen = RenderedText.of(
                    testRunner,
                    () -> CommandLogPanel.fitted(model, 4, Option.none(), Theme.SpanStyles.NONE, ZoneOffset.UTC));
            assertFalse(screen.contains("old.txt"), screen);
            assertTrue(screen.contains("git add -- new.txt"), screen);
            assertTrue(screen.contains("Checkout of foo failed: boom"), screen);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void givesTheErrorTheRowsItWrapsOnto() throws Exception {
        assertShowsTheEndOfTheError("boom boom boom boom boom boom boom boom END", 24);
    }

    @Test
    void givesAnErrorWithRunsOfSpacesTheRowsItWrapsOnto() throws Exception {
        // As git reports a rejected push: the spaces are drawn as they are, not as one.
        assertShowsTheEndOfTheError("a  b  c  d  e  f  g  h  i  j  k  l  m  n  o  p END", 16);
    }

    @Test
    void givesAnErrorWithIndentedLinesTheRowsItWrapsOnto() throws Exception {
        // Each row an indented line wraps onto starts with its indent too.
        assertShowsTheEndOfTheError("x:\n    indented hint text here that is long END", 20);
    }

    @Test
    void keepsARowForTheErrorWhenTheContentPanelSparesNone() {
        Model model = TestModels.withCheckoutError(
                TestModels.withLog(
                        TestModels.loaded(SampleData.git()), entry(PushCmd.PUSH, executed("git push origin main"))),
                "foo",
                "boom");

        assertEquals(2 + 1, CommandLogPanel.height(model, room(5)));
    }

    /**
     * The checkout error with {@code explanation}, which ends in {@code END}, shown whole in a log {@code columns}
     * wide: as tall as it wraps, and drawn so in that room.
     */
    private static void assertShowsTheEndOfTheError(String explanation, int columns) throws Exception {
        Model model = TestModels.withCheckoutError(TestModels.loaded(SampleData.git()), "foo", explanation);
        Option<CommandLogPanel.Room> room = Option.of(new CommandLogPanel.Room(40, columns));
        int rows = CommandLogPanel.height(model, room);

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(
                () -> CommandLogPanel.fitted(model, rows, room, Theme.SpanStyles.NONE, ZoneOffset.UTC))) {
            String screen = RenderedText.ofSize(
                    testRunner,
                    () -> CommandLogPanel.fitted(model, rows, room, Theme.SpanStyles.NONE, ZoneOffset.UTC),
                    columns,
                    rows);
            assertTrue(screen.contains("END"), screen);

            SettledPilot.of(testRunner).quit();
        }
    }

    /**
     * Room for the log in a column {@code rows} tall and wide enough for its lines.
     */
    @Test
    void withTheFocusGrowsToHalfTheColumnOrAsTallAsAllItLogged() {
        CommandLogPanel panel = new CommandLogPanel(Theme.SpanStyles.NONE, ZoneOffset.UTC, id -> {}, () -> {});
        Model model = TestModels.withLog(
                TestModels.loaded(SampleData.git()),
                entry(StageCmd.STAGE, executed("git add -- a", "git add -- b", "git add -- c")),
                entry(StageCmd.STAGE, executed("git add -- d", "git add -- e", "git add -- f")),
                entry(StageCmd.STAGE, executed("git add -- g", "git add -- h", "git add -- i")));

        // Twelve lines and the borders, which half of 40 rows holds.
        assertEquals(2 + 12, panel.focusedHeight(model, room(40)));
        // Half of 24 rows, more than the 10 it has without the focus.
        assertEquals(12, panel.focusedHeight(model, room(24)));
        // On a short screen too, where it has 4 rows without the focus.
        assertEquals(4, CommandLogPanel.height(model, room(16)));
        assertEquals(8, panel.focusedHeight(model, room(16)));
        Model empty = TestModels.loaded(SampleData.git());
        assertEquals(CommandLogPanel.BORDERS, panel.focusedHeight(empty, room(40)));
    }

    @Test
    void withTheFocusShowsAllItLoggedFromTheNewestAndScrolls() throws Exception {
        CommandLogPanel panel = new CommandLogPanel(Theme.SpanStyles.NONE, ZoneOffset.UTC, id -> {}, () -> {});
        Model model = TestModels.withLog(
                TestModels.loaded(SampleData.git()),
                entry(StageCmd.STAGE, executed("git add -- oldest.txt", "git add -- b", "git add -- c")),
                entry(StageCmd.STAGE, executed("git add -- d", "git add -- e", "git add -- f")),
                entry(StageCmd.STAGE, executed("git add -- g", "git add -- h", "git add -- newest.txt")));
        Option<CommandLogPanel.Room> room = Option.of(new CommandLogPanel.Room(40, 60));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(
                () -> panel.render(model, 6, 6, room, true, true), new dev.tamboui.layout.Size(60, 6))) {
            String newest = RenderedText.ofSize(testRunner, () -> panel.render(model, 6, 6, room, true, true), 60, 6);
            assertTrue(newest.contains("newest.txt"), newest);
            assertTrue(newest.contains("14:32:05 Stage"), newest);
            assertFalse(newest.contains("oldest.txt"), newest);

            SettledPilot.focus(testRunner, CommandLogPanel.ID);
            var pilot = SettledPilot.of(testRunner);
            pilot.press('g');
            pilot.pause();

            String oldest = RenderedText.ofSize(testRunner, () -> panel.render(model, 6, 6, room, true, true), 60, 6);
            assertTrue(oldest.contains("oldest.txt"), oldest);
            assertFalse(oldest.contains("newest.txt"), oldest);

            pilot.quit();
        }
    }

    @Test
    void withTheFocusShowsTheNewestLinesOnItsWayToItsHeight() throws Exception {
        CommandLogPanel panel = new CommandLogPanel(Theme.SpanStyles.NONE, ZoneOffset.UTC, id -> {}, () -> {});
        Model model = TestModels.withLog(
                TestModels.loaded(SampleData.git()),
                entry(StageCmd.STAGE, executed("git add -- oldest.txt", "git add -- b", "git add -- c")),
                entry(StageCmd.STAGE, executed("git add -- d", "git add -- e", "git add -- f")),
                entry(StageCmd.STAGE, executed("git add -- g", "git add -- h", "git add -- newest.txt")));
        Option<CommandLogPanel.Room> room = Option.of(new CommandLogPanel.Room(40, 60));

        // On its way to the 14 rows that show all, it is 6 rows tall.
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(
                () -> panel.render(model, 14, 6, room, true, true), new dev.tamboui.layout.Size(60, 6))) {
            String sliding = RenderedText.ofSize(testRunner, () -> panel.render(model, 14, 6, room, true, true), 60, 6);
            assertTrue(sliding.contains("newest.txt"), sliding);
            assertFalse(sliding.contains("oldest.txt"), sliding);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void withTheFocusShowsTheWholeErrorWrapped() throws Exception {
        CommandLogPanel panel = new CommandLogPanel(Theme.SpanStyles.NONE, ZoneOffset.UTC, id -> {}, () -> {});
        Model model = TestModels.withCheckoutError(
                TestModels.loaded(SampleData.git()), "foo", "a  b  c  d  e  f  g  h  i  j  k  l  m  n  o  p END");
        Option<CommandLogPanel.Room> room = Option.of(new CommandLogPanel.Room(40, 16));
        int rows = panel.focusedHeight(model, room);

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(model, rows, rows, room, true, true))) {
            String screen =
                    RenderedText.ofSize(testRunner, () -> panel.render(model, rows, rows, room, true, true), 16, rows);
            assertTrue(screen.contains("Checkout"), screen);
            assertTrue(screen.contains("END"), screen);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void withTheFocusATitleWrapsUnderItself() throws Exception {
        CommandLogPanel panel = new CommandLogPanel(Theme.SpanStyles.NONE, ZoneOffset.UTC, id -> {}, () -> {});
        Model model = TestModels.withLog(
                TestModels.loaded(SampleData.git()), entry(PullRequestCmd.DRAFT_PR, executed("gh pr ready --undo 7")));
        // 22 columns inside the borders: 13 after the time for "Convert pull request to draft".
        Option<CommandLogPanel.Room> room = Option.of(new CommandLogPanel.Room(40, 24));
        int rows = panel.focusedHeight(model, room);

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(model, rows, rows, room, true, true))) {
            String screen =
                    RenderedText.ofSize(testRunner, () -> panel.render(model, rows, rows, room, true, true), 24, rows);
            assertTrue(screen.contains("14:32:05 Convert pull"), screen);
            assertTrue(screen.contains("         request to"), screen);
            assertTrue(screen.contains("         draft"), screen);
            assertTrue(screen.contains("gh pr ready --undo 7"), screen);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void withTheFocusScrolledUpItStaysAsLinesAreAddedUntilItIsBackAtTheBottom() throws Exception {
        CommandLogPanel panel = new CommandLogPanel(Theme.SpanStyles.NONE, ZoneOffset.UTC, id -> {}, () -> {});
        AtomicReference<Model> model = new AtomicReference<>(staged(4));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> open(panel, model.get()), SMALL)) {
            SettledPilot.focus(testRunner, CommandLogPanel.ID);
            Pilot pilot = SettledPilot.of(testRunner);
            pilot.press('k');
            pilot.pause();

            // Scrolled up, a line added stays out of sight.
            model.set(staged(5));
            String up = drawn(testRunner, panel, model.get());
            assertFalse(up.contains("file5.txt"), up);
            assertTrue(up.contains("file3.txt"), up);

            // Back at the bottom, it follows the lines added again.
            pilot.press('G');
            pilot.pause();
            model.set(staged(6));
            String bottom = drawn(testRunner, panel, model.get());
            assertTrue(bottom.contains("file6.txt"), bottom);

            pilot.quit();
        }
    }

    @Test
    void withTheFocusAgainItStartsFromTheNewest() throws Exception {
        CommandLogPanel panel = new CommandLogPanel(Theme.SpanStyles.NONE, ZoneOffset.UTC, id -> {}, () -> {});
        Model model = staged(4);
        Option<CommandLogPanel.Room> room = Option.of(new CommandLogPanel.Room(40, 60));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> open(panel, model), SMALL)) {
            SettledPilot.focus(testRunner, CommandLogPanel.ID);
            Pilot pilot = SettledPilot.of(testRunner);
            pilot.press('g');
            pilot.pause();
            assertTrue(drawn(testRunner, panel, model).contains("file1.txt"));

            // Left, as Escape leaves it, and given the focus again.
            RenderedText.ofSize(testRunner, () -> panel.render(model, 6, 6, room, false, false), 60, 6);
            String again = drawn(testRunner, panel, model);
            assertTrue(again.contains("file4.txt"), again);
            assertFalse(again.contains("file1.txt"), again);

            pilot.quit();
        }
    }

    @Test
    void withTheFocusTheWheelScrollsIt() throws Exception {
        CommandLogPanel panel = new CommandLogPanel(Theme.SpanStyles.NONE, ZoneOffset.UTC, id -> {}, () -> {});
        AtomicReference<Model> model = new AtomicReference<>(staged(4));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> open(panel, model.get()), SMALL)) {
            SettledPilot.focus(testRunner, CommandLogPanel.ID);
            // Eight lines, four of which show: three up from the bottom shows the second.
            testRunner
                    .runner()
                    .tuiRunner()
                    .dispatch(new MouseEvent(MouseEventKind.SCROLL_UP, MouseButton.NONE, 5, 2, KeyModifiers.NONE));
            SettledPilot.settle(testRunner);

            String up = drawn(testRunner, panel, model.get());
            assertTrue(up.contains("file1.txt"), up);
            assertFalse(up.contains("file4.txt"), up);
            // It stays there as lines are added.
            model.set(staged(5));
            assertFalse(drawn(testRunner, panel, model.get()).contains("file5.txt"));

            SettledPilot.of(testRunner).quit();
        }
    }

    /**
     * A screen as wide as the log and as tall as it is drawn in the tests of scrolling it: four lines inside its
     * borders.
     */
    private static final dev.tamboui.layout.Size SMALL = new dev.tamboui.layout.Size(60, 6);

    /**
     * {@code model} with {@code count} files staged one at a time, the oldest first: two lines each.
     */
    private static Model staged(int count) {
        return TestModels.withLog(
                TestModels.loaded(SampleData.git()),
                List.rangeClosed(1, count)
                        .map(n -> entry(StageCmd.STAGE, executed("git add -- file" + n + ".txt")))
                        .toJavaArray(LogEntry[]::new));
    }

    /**
     * {@code panel} with the focus, showing {@code model} in the {@link #SMALL} screen.
     */
    private static dev.tamboui.toolkit.element.Element open(CommandLogPanel panel, Model model) {
        return panel.render(model, 6, 6, Option.of(new CommandLogPanel.Room(40, 60)), true, true);
    }

    /**
     * What {@code panel} with the focus draws of {@code model}, where it is scrolled to.
     */
    private static String drawn(ToolkitTestRunner testRunner, CommandLogPanel panel, Model model) throws Exception {
        return RenderedText.ofSize(testRunner, () -> open(panel, model), 60, 6);
    }

    private static Option<CommandLogPanel.Room> room(int rows) {
        return Option.of(new CommandLogPanel.Room(rows, 200));
    }

    /**
     * The panel of {@code model} as tall as its lines.
     */
    private static Panel shown(Model model) {
        return CommandLogPanel.fitted(
                model,
                CommandLogPanel.height(model, Option.none()),
                Option.none(),
                Theme.SpanStyles.NONE,
                ZoneOffset.UTC);
    }

    /**
     * {@code model} with the content panel maximized: the left column of the repository screen collapsed.
     */
    private static Model collapsed(Model model) {
        return model.mapPreferences(preferences -> preferences.withLeftCollapsed(Screen.REPOSITORY, true));
    }

    /**
     * When the operations of the log entries of these tests were done: 14:32:05 in UTC.
     */
    private static final Instant AT = Instant.parse("2026-11-14T14:32:05Z");

    /**
     * An entry for {@code commands} of {@code operation}, done {@link #AT}.
     */
    private static LogEntry entry(Operation operation, List<ExecutedCommand> commands) {
        return new LogEntry(operation, commands, AT);
    }
}
