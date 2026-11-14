package net.vanfleteren.jazygit.ui;

import static net.vanfleteren.jazygit.git.StubGit.executed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.elements.Panel;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd;
import net.vanfleteren.jazygit.feature.commit.create.CreateCommitCmd;
import net.vanfleteren.jazygit.feature.stage.StageCmd;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.operation.LogEntry;
import net.vanfleteren.jazygit.state.operation.Notice;
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
                TestModels.loaded(SampleData.git()), new LogEntry(PushCmd.PUSH, executed("git push origin main")));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> shown(model))) {
            String screen = RenderedText.of(testRunner, () -> shown(model));
            assertTrue(screen.contains("Push"), screen);
            assertTrue(screen.contains("  git push origin main"), screen);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void keepsTheNewestLinesWhenTheyDoNotAllFit() throws Exception {
        Model model = TestModels.withLog(
                TestModels.loaded(SampleData.git()),
                new LogEntry(StageCmd.STAGE, executed("git add -- old.txt")),
                new LogEntry(
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
                TestModels.loaded(SampleData.git()), new LogEntry(PushCmd.PUSH, executed("git push origin main")));
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
                TestModels.loaded(SampleData.git()), new LogEntry(PushCmd.PUSH, executed("git push origin main")));
        Model many = TestModels.withLog(
                TestModels.loaded(SampleData.git()),
                new LogEntry(StageCmd.STAGE, executed("git add -- a", "git add -- b", "git add -- c")),
                new LogEntry(StageCmd.STAGE, executed("git add -- d", "git add -- e", "git add -- f")));

        // The title of the operation and its command.
        assertEquals(2 + 2, CommandLogPanel.height(one, Option.none()));
        assertEquals(2 + CommandLogPanel.VISIBLE_LINES, CommandLogPanel.height(many, Option.none()));
    }

    @Test
    void takesOnlyTheRowsTheContentPanelSpares() {
        Model model = TestModels.withLog(
                TestModels.loaded(SampleData.git()),
                new LogEntry(StageCmd.STAGE, executed("git add -- a", "git add -- b", "git add -- c")),
                new LogEntry(StageCmd.STAGE, executed("git add -- d", "git add -- e", "git add -- f")));

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
                        new LogEntry(StageCmd.STAGE, executed("git add -- old.txt", "git add -- new.txt"))),
                "foo",
                "boom");

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> CommandLogPanel.fitted(model, 4, Option.none()))) {
            String screen = RenderedText.of(testRunner, () -> CommandLogPanel.fitted(model, 4, Option.none()));
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
                        TestModels.loaded(SampleData.git()),
                        new LogEntry(PushCmd.PUSH, executed("git push origin main"))),
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

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> CommandLogPanel.fitted(model, rows, room))) {
            String screen =
                    RenderedText.ofSize(testRunner, () -> CommandLogPanel.fitted(model, rows, room), columns, rows);
            assertTrue(screen.contains("END"), screen);

            SettledPilot.of(testRunner).quit();
        }
    }

    /**
     * Room for the log in a column {@code rows} tall and wide enough for its lines.
     */
    @Test
    void withTheFocusGrowsToHalfTheColumnOrAsTallAsAllItLogged() {
        CommandLogPanel panel = new CommandLogPanel(Theme.SpanStyles.NONE, id -> {}, () -> {});
        Model model = TestModels.withLog(
                TestModels.loaded(SampleData.git()),
                new LogEntry(StageCmd.STAGE, executed("git add -- a", "git add -- b", "git add -- c")),
                new LogEntry(StageCmd.STAGE, executed("git add -- d", "git add -- e", "git add -- f")),
                new LogEntry(StageCmd.STAGE, executed("git add -- g", "git add -- h", "git add -- i")));

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
        CommandLogPanel panel = new CommandLogPanel(Theme.SpanStyles.NONE, id -> {}, () -> {});
        Model model = TestModels.withLog(
                TestModels.loaded(SampleData.git()),
                new LogEntry(StageCmd.STAGE, executed("git add -- oldest.txt", "git add -- b", "git add -- c")),
                new LogEntry(StageCmd.STAGE, executed("git add -- d", "git add -- e", "git add -- f")),
                new LogEntry(StageCmd.STAGE, executed("git add -- g", "git add -- h", "git add -- newest.txt")));
        Option<CommandLogPanel.Room> room = Option.of(new CommandLogPanel.Room(40, 60));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(
                () -> panel.render(model, 6, 6, room, true), new dev.tamboui.layout.Size(60, 6))) {
            String newest = RenderedText.ofSize(testRunner, () -> panel.render(model, 6, 6, room, true), 60, 6);
            assertTrue(newest.contains("newest.txt"), newest);
            assertFalse(newest.contains("oldest.txt"), newest);

            SettledPilot.focus(testRunner, CommandLogPanel.ID);
            var pilot = SettledPilot.of(testRunner);
            pilot.press('g');
            pilot.pause();

            String oldest = RenderedText.ofSize(testRunner, () -> panel.render(model, 6, 6, room, true), 60, 6);
            assertTrue(oldest.contains("oldest.txt"), oldest);
            assertFalse(oldest.contains("newest.txt"), oldest);

            pilot.quit();
        }
    }

    @Test
    void withTheFocusShowsTheWholeErrorWrapped() throws Exception {
        CommandLogPanel panel = new CommandLogPanel(Theme.SpanStyles.NONE, id -> {}, () -> {});
        Model model = TestModels.withCheckoutError(
                TestModels.loaded(SampleData.git()), "foo", "a  b  c  d  e  f  g  h  i  j  k  l  m  n  o  p END");
        Option<CommandLogPanel.Room> room = Option.of(new CommandLogPanel.Room(40, 16));
        int rows = panel.focusedHeight(model, room);

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(model, rows, rows, room, true))) {
            String screen =
                    RenderedText.ofSize(testRunner, () -> panel.render(model, rows, rows, room, true), 16, rows);
            assertTrue(screen.contains("Checkout"), screen);
            assertTrue(screen.contains("END"), screen);

            SettledPilot.of(testRunner).quit();
        }
    }

    private static Option<CommandLogPanel.Room> room(int rows) {
        return Option.of(new CommandLogPanel.Room(rows, 200));
    }

    /**
     * The panel of {@code model} as tall as its lines.
     */
    private static Panel shown(Model model) {
        return CommandLogPanel.fitted(model, CommandLogPanel.height(model, Option.none()), Option.none());
    }

    /**
     * {@code model} with the content panel maximized: the left column of the repository screen collapsed.
     */
    private static Model collapsed(Model model) {
        return model.mapPreferences(preferences -> preferences.withLeftCollapsed(Screen.REPOSITORY, true));
    }
}
