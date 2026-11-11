package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.load.SelectionMsg;
import net.vanfleteren.jazygit.state.view.FileFilter;
import org.junit.jupiter.api.Test;

/**
 * The files pane listing all files of the working tree (ADR 108): the files without changes among the changed ones,
 * with no marker, opening only on the changes, and the content panel told what a node without changes is.
 */
class FilesPanelAllFilesTest {

    private static final FileEntry CHANGED = new FileEntry("src/main/A.java", ChangeType.MODIFIED, false, true);
    // Rows: /, docs/guide, src/main, M A.java, B.java, README.md: directories come first.
    private static final List<String> ALL =
            List.of("README.md", "docs/guide/intro.md", "src/main/A.java", "src/main/B.java");

    private final FilesPanel panel = new FilesPanel(msg -> {}, () -> {});

    private static Model all(boolean listed) {
        Model loaded = TestModels.loaded();
        return loaded.mapRepo(repo -> repo.withStatus(repo.status().map(status -> status.withFiles(List.of(CHANGED))))
                        .withAllFiles(new Loadable.Loaded<>(ALL)))
                .mapRepoView(view -> view.withAllFiles(listed));
    }

    @Test
    void allFilesAreListedThoseWithoutChangesWithoutAMarkerAndCollapsedUnlessTheyHoldChanges() throws Exception {
        Model model = all(true);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, FilesPanel.ID))) {
            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(model, FilesPanel.ID));

            assertTrue(screen.contains("Files · all files"), screen);
            assertTrue(screen.contains(" M A.java"), screen);
            assertTrue(screen.contains("   B.java"), screen);
            assertTrue(screen.contains("README.md"), screen);
            assertTrue(screen.contains("docs/guide"), screen);
            // A directory without changes starts collapsed.
            assertFalse(screen.contains("intro.md"), screen);
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void theChangedFilesKeepTheirOwnTree() throws Exception {
        Model changed = all(false);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(changed, FilesPanel.ID))) {
            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(changed, FilesPanel.ID));

            assertFalse(screen.contains("all files"), screen);
            assertFalse(screen.contains("README.md"), screen);
            assertTrue(screen.contains(" M A.java"), screen);
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void aNodeWithoutChangesIsReadOrCountedAndOneWithChangesShowsItsDiff() throws Exception {
        Model model = all(true);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, FilesPanel.ID))) {
            RenderedText.ofLarge(testRunner, () -> panel.render(model, FilesPanel.ID));

            panel.allTree().selected(5);
            assertEquals(Option.of(new SelectionMsg.UnchangedFileSelected("README.md")), panel.selectionChange(model));

            panel.allTree().selected(1);
            assertEquals(
                    Option.of(new SelectionMsg.UnchangedDirectorySelected("docs/guide", 1)),
                    panel.selectionChange(model));

            panel.allTree().selected(2);
            // Its diff is of its changed files only, as without all files.
            assertEquals(Option.of(new SelectionMsg.FilesSelected(List.of(CHANGED))), panel.selectionChange(model));

            panel.allTree().selected(4);
            assertEquals(
                    Option.of(new SelectionMsg.UnchangedFileSelected("src/main/B.java")), panel.selectionChange(model));
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void theFilterNarrowsAllFiles() throws Exception {
        Model filtered =
                all(true).mapRepoView(view -> view.withFilesFilter(Option.of(new FileFilter("intro", Option.none()))));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(filtered, FilesPanel.ID))) {
            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(filtered, FilesPanel.ID));

            assertTrue(screen.contains("Files · all files · /intro"), screen);
            assertTrue(screen.contains("intro.md"), screen);
            assertFalse(screen.contains("A.java"), screen);
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void onlyTheDirectoriesWithoutChangesStartCollapsed() {
        List<FileEntry> files = List.of(
                CHANGED,
                FileEntry.unchanged("src/main/B.java"),
                FileEntry.unchanged("src/test/BTest.java"),
                FileEntry.unchanged("docs/guide/intro.md"));

        assertEquals(HashSet.of("src/test", "docs/guide"), FilesPanel.unchangedDirectories(files));
    }

    @Test
    void untilAllFilesAreReadTheTreeSaysItIsLoading() throws Exception {
        Model reading = all(true).mapRepo(repo -> repo.withAllFiles(Loadable.loading()));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(reading, FilesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(reading, FilesPanel.ID));

            assertTrue(screen.contains(Placeholders.loading()), screen);
            SettledPilot.of(testRunner).quit();
        }
    }
}
