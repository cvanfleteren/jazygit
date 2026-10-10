package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The command log panel shows why the last operation failed.
 */
class CommandLogPanelTest {

    @Test
    void showsTheLastOperationError() throws Exception {
        Model model = TestModels.withError(TestModels.loaded(new SampleData()), "Checkout of foo failed: boom");

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> CommandLogPanel.render(model))) {
            String screen = RenderedText.of(testRunner, () -> CommandLogPanel.render(model));
            assertTrue(screen.contains("Command log"), screen);
            assertTrue(screen.contains("Checkout of foo failed: boom"), screen);

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
