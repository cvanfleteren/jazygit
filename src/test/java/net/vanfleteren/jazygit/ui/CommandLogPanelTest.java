package net.vanfleteren.jazygit.ui;

import static net.vanfleteren.jazygit.git.StubGitProvider.executed;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.LogEntry;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.TestModels;
import org.junit.jupiter.api.Test;

/**
 * The command log panel shows why the last operation failed.
 */
class CommandLogPanelTest {

    @Test
    void showsTheLastOperationError() throws Exception {
        Model model = TestModels.withCheckoutError(TestModels.loaded(new SampleData()), "foo", "boom");

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> CommandLogPanel.render(model))) {
            String screen = RenderedText.of(testRunner, () -> CommandLogPanel.render(model));
            assertTrue(screen.contains("Command log"), screen);
            assertTrue(screen.contains("Checkout of foo failed: boom"), screen);

            testRunner.pilot().quit();
        }
    }

    @Test
    void showsExecutedCommandsUnderTheirTitle() throws Exception {
        Model model = TestModels.withLog(
                TestModels.loaded(new SampleData()), new LogEntry(Operation.PUSH, executed("git push origin main")));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> CommandLogPanel.render(model))) {
            String screen = RenderedText.of(testRunner, () -> CommandLogPanel.render(model));
            assertTrue(screen.contains("Push"), screen);
            assertTrue(screen.contains("  git push origin main"), screen);

            testRunner.pilot().quit();
        }
    }

    @Test
    void keepsTheNewestLinesWhenTheyDoNotAllFit() throws Exception {
        Model model = TestModels.withLog(
                TestModels.loaded(new SampleData()),
                new LogEntry(Operation.STAGE, executed("git add -- old.txt")),
                new LogEntry(
                        Operation.COMMIT,
                        executed(
                                "git commit -m a",
                                "git commit -m b",
                                "git commit -m c",
                                "git commit -m d",
                                "git commit -m e",
                                "git commit -m f",
                                "git commit -m g")));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> CommandLogPanel.render(model))) {
            String screen = RenderedText.ofLarge(testRunner, () -> CommandLogPanel.render(model));
            assertFalse(screen.contains("old.txt"), screen);
            assertTrue(screen.contains("git commit -m g"), screen);

            testRunner.pilot().quit();
        }
    }

    @Test
    void isEmptyWithoutAnError() throws Exception {
        Model model = TestModels.loaded(new SampleData());

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> CommandLogPanel.render(model))) {
            String screen = RenderedText.of(testRunner, () -> CommandLogPanel.render(model));
            assertTrue(screen.contains("Command log"), screen);
            assertFalse(screen.contains("failed"), screen);

            testRunner.pilot().quit();
        }
    }
}
