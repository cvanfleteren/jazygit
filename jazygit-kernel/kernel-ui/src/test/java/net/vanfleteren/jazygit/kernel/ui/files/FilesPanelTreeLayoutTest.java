package net.vanfleteren.jazygit.kernel.ui.files;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.git.model.ChangeType;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * How little width the tree of files spends on its layout: two cells a level, without guide lines, and a long
 * directory shortened in the middle.
 */
class FilesPanelTreeLayoutTest {

    private final FilesPanel panel = new FilesPanel(msg -> {}, () -> {});

    private static Model changed(String... paths) {
        List<FileEntry> files = List.of(paths).map(path -> new FileEntry(path, ChangeType.MODIFIED, false, true));
        return TestModels.loaded()
                .mapRepo(repo -> repo.withStatus(repo.status().map(status -> status.withFiles(files))));
    }

    private static List<String> rows(String screen) {
        return List.of(screen.split("\n"));
    }

    private static int column(String screen, String node) {
        return rows(screen)
                .find(row -> row.contains(node))
                .map(row -> row.indexOf(node))
                .getOrElse(-1);
    }

    @Test
    void aLevelOfDepthTakesTwoCells() throws Exception {
        Model model = changed("src/main/A.java", "src/main/deep/B.java", "src/test/C.java");
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, FilesPanel.PANE))) {
            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(model, FilesPanel.PANE));

            assertEquals(column(screen, "▼ src") + 2, column(screen, "▼ main"), screen);
            assertEquals(column(screen, "▼ main") + 2, column(screen, "▼ deep"), screen);
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void theTreeDrawsNoGuideLines() throws Exception {
        Model model = changed("src/main/A.java", "src/main/deep/B.java", "src/test/C.java");
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, FilesPanel.PANE))) {
            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(model, FilesPanel.PANE));

            assertFalse(rows(screen).exists(row -> row.matches("│.*[├└│─].*│")), screen);
            assertTrue(rows(screen).exists(row -> row.matches("│\\s+▼ deep\\s+│")), screen);
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void aDirectoryTooLongForThePaneKeepsBothEnds() throws Exception {
        Model model = changed(
                "module/src/main/java/net/example/project/feature/A.java",
                "module/src/main/java/net/example/project/feature/B.java");
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, FilesPanel.PANE))) {
            String screen = RenderedText.ofSize(testRunner, () -> panel.render(model, FilesPanel.PANE), 30, 8);

            assertTrue(screen.contains("module/src...t/feature"), screen);
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void anErrorTooLongForThePaneIsCutAtItsEnd() throws Exception {
        String reason = "fatal: not a git repository (or any of the parent directories)";
        Model failed =
                TestModels.loaded().mapRepo(repo -> repo.withStatus(new Loadable.Failed<>(TestModels.refused(reason))));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(failed, FilesPanel.PANE))) {
            String screen = RenderedText.ofSize(testRunner, () -> panel.render(failed, FilesPanel.PANE), 30, 8);

            assertTrue(screen.contains("fatal: not"), screen);
            assertFalse(screen.contains("...directories"), screen);
            SettledPilot.of(testRunner).quit();
        }
    }
}
