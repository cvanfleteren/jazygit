package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The status panel shows the repository name and the current branch.
 */
class StatusPanelTest {

    @Test
    void showsRepositoryNameAndCurrentBranch() throws Exception {
        Model model = TestModels.loaded(new SampleData());

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> StatusPanel.render(model))) {
            String screen = RenderedText.of(testRunner, () -> StatusPanel.render(model));
            assertTrue(screen.contains("Status"), screen);
            assertTrue(screen.contains("jazygit → main"), screen);

            String loading = RenderedText.of(testRunner, () -> StatusPanel.render(TestModels.loading("jazygit")));
            assertTrue(loading.contains("jazygit → " + Placeholders.loading()), loading);

            testRunner.pilot().quit();
        }
    }

    @Test
    void doesNotShowTheLastOperationError() throws Exception {
        Model model = TestModels.withError(TestModels.loaded(new SampleData()), "Checkout of foo failed: boom");

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> StatusPanel.render(model))) {
            String screen = RenderedText.of(testRunner, () -> StatusPanel.render(model));
            assertTrue(screen.contains("jazygit → main"), screen);
            assertFalse(screen.contains("Checkout of foo failed: boom"), screen);

            testRunner.pilot().quit();
        }
    }
}
