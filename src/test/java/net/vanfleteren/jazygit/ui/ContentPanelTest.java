package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.elements.Panel;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests on {@link ContentPanel}, exercising its three view branches (file status,
 * branch log, commit diff) and boundary selection indices, without needing a live TUI runner.
 */
class ContentPanelTest {

    private final Model model = TestModels.loaded(new SampleData());

    @Test
    void filesFocusedRendersFileStatusView() {
        Panel panel = ContentPanel.render(model, FilesPanel.ID, 0, 0);
        assertNotNull(panel);
    }

    @Test
    void branchesFocusedRendersBranchLogView() {
        Panel panel = ContentPanel.render(model, BranchesPanel.ID, 1, 0);
        assertNotNull(panel);
    }

    @Test
    void commitsFocusedRendersCommitDiffView() {
        Panel panel = ContentPanel.render(model, CommitsPanel.ID, 0, 2);
        assertNotNull(panel);
    }

    @Test
    void unknownFocusFallsBackToFileStatusView() {
        Panel panel = ContentPanel.render(model, "unknown", 0, 0);
        assertNotNull(panel);
    }

    @Test
    void rendersPlaceholdersWhileLoading() {
        Model loading = TestModels.loading("repo");
        assertNotNull(ContentPanel.render(loading, FilesPanel.ID, 0, 0));
        assertNotNull(ContentPanel.render(loading, BranchesPanel.ID, 0, 0));
        assertNotNull(ContentPanel.render(loading, CommitsPanel.ID, 0, 0));
    }

    @Test
    void outOfRangeSelectionIsClampedRatherThanThrowing() {
        assertDoesNotThrow(() -> ContentPanel.render(model, BranchesPanel.ID, -5, 0));
        assertDoesNotThrow(() -> ContentPanel.render(model, BranchesPanel.ID, 999, 0));
        assertDoesNotThrow(() -> ContentPanel.render(model, CommitsPanel.ID, 0, -5));
        assertDoesNotThrow(() -> ContentPanel.render(model, CommitsPanel.ID, 0, 999));
    }
}
