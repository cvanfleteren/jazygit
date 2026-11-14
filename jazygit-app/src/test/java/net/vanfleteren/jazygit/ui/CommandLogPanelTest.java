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
        // Never fewer than its borders, which show its title.
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
                ToolkitTestRunner.runTest(() -> CommandLogPanel.render(model, 4, Option.none()))) {
            String screen = RenderedText.of(testRunner, () -> CommandLogPanel.render(model, 4, Option.none()));
            assertFalse(screen.contains("old.txt"), screen);
            assertTrue(screen.contains("git add -- new.txt"), screen);
            assertTrue(screen.contains("Checkout of foo failed: boom"), screen);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void givesTheErrorTheRowsItWrapsOnto() throws Exception {
        Model model = TestModels.withCheckoutError(
                TestModels.loaded(SampleData.git()), "foo", "boom boom boom boom boom boom boom boom");
        // "Checkout of foo failed: boom boom ..." in 22 columns inside the borders.
        Option<CommandLogPanel.Room> room = Option.of(new CommandLogPanel.Room(40, 24));
        int rows = CommandLogPanel.height(model, room);

        assertEquals(2 + 4, rows);
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> CommandLogPanel.render(model, rows, room))) {
            String screen = RenderedText.of(
                    testRunner, () -> CommandLogPanel.render(model, rows, room).length(rows));
            assertTrue(screen.contains("failed: boom"), screen);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void wrapsAtWords() {
        assertEquals(1, CommandLogPanel.wrappedRows("one two", 7));
        assertEquals(2, CommandLogPanel.wrappedRows("one two", 6));
        assertEquals(3, CommandLogPanel.wrappedRows("one\ntwo three", 6));
        // A word wider than a row is cut over the rows it takes.
        assertEquals(3, CommandLogPanel.wrappedRows("a abcdefghij", 5));
    }

    /**
     * Room for the log in a column {@code rows} tall and wide enough for its lines.
     */
    private static Option<CommandLogPanel.Room> room(int rows) {
        return Option.of(new CommandLogPanel.Room(rows, 200));
    }

    /**
     * The panel of {@code model} as tall as its lines.
     */
    private static Panel shown(Model model) {
        return CommandLogPanel.render(model, CommandLogPanel.height(model, Option.none()), Option.none());
    }

    /**
     * {@code model} with the content panel maximized: the left column of the repository screen collapsed.
     */
    private static Model collapsed(Model model) {
        return model.mapPreferences(preferences -> preferences.withLeftCollapsed(Screen.REPOSITORY, true));
    }
}
