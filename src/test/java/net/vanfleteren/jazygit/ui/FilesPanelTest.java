package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Conflict;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.FileTree;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The files panel must follow the model as the working tree status changes.
 */
class FilesPanelTest {

    private record FixedProvider(List<FileEntry> files) implements StubGitProvider {

        @Override
        public Either<ProcessError, RepoStatus> status() {
            return Either.right(new RepoStatus("main", "aaaa", files));
        }
    }

    private final FilesPanel panel = new FilesPanel(msg -> { });

    @Test
    void conflictedFilesShowAMarkerForEachBranchThatChangedThem() throws Exception {
        Model updated = TestModels.loaded(new FixedProvider(List.of(
                FileEntry.conflicted("both.txt", Conflict.of("UU")),
                FileEntry.conflicted("added.txt", Conflict.of("AA")))));
        Model deleted = TestModels.loaded(new FixedProvider(List.of(
                FileEntry.conflicted("theirs-deleted.txt", Conflict.of("UD")),
                FileEntry.conflicted("ours-deleted.txt", Conflict.of("DU")))));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(updated, FilesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(updated, FilesPanel.ID));
            assertTrue(screen.contains("UU both.txt"), screen);
            assertTrue(screen.contains("UU added.txt"), screen);

            String other = RenderedText.of(testRunner, () -> panel.render(deleted, FilesPanel.ID));
            assertTrue(other.contains("UD theirs-deleted.txt"), other);
            assertTrue(other.contains("DU ours-deleted.txt"), other);

            testRunner.pilot().quit();
        }
    }

    @Test
    void aPlaceholderRowIsNotASelection() throws Exception {
        Model loading = TestModels.loading("repo");

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(loading, FilesPanel.ID))) {
            RenderedText.of(testRunner, () -> panel.render(loading, FilesPanel.ID));

            assertTrue(panel.selectionChange(loading).isEmpty());
        }
    }

    @Test
    void rendersLoadingThenStatusChanges() throws Exception {
        Model untracked = TestModels.loaded(new FixedProvider(List.of(new FileEntry("a.txt", ChangeType.UNTRACKED))));
        Model added = TestModels.loaded(new FixedProvider(List.of(new FileEntry("a.txt", ChangeType.ADDED))));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(untracked, FilesPanel.ID))) {
            String loading = RenderedText.of(testRunner, () -> panel.render(TestModels.loading("repo"), FilesPanel.ID));
            assertTrue(loading.contains(Placeholders.loading()), loading);

            String initial = RenderedText.of(testRunner, () -> panel.render(untracked, FilesPanel.ID));
            assertTrue(initial.contains("?? a.txt"), initial);

            String screen = RenderedText.of(testRunner, () -> panel.render(added, FilesPanel.ID));
            assertTrue(screen.contains("A  a.txt"), screen);
            assertFalse(screen.contains("?? a.txt"), screen);

            testRunner.pilot().quit();
        }
    }

    @Test
    void rendersEmptyPanelWithoutRootWhenThereAreNoFiles() throws Exception {
        Model empty = TestModels.loaded(new FixedProvider(List.of()));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(empty, FilesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(empty, FilesPanel.ID));
            assertTrue(screen.contains("Files"), screen);
            assertTrue(screen.contains("0/0"), screen);
            // Apart from the position, there is no "/" root row.
            assertFalse(screen.replace("0/0", "").contains("/"), screen);

            testRunner.pilot().quit();
        }
    }

    @Test
    void rendersMergedDirectoriesAndKeepsSelectionWhenStatusChanges()throws Exception {
        FileEntry a = new FileEntry("net/vanfleteren/A.java", ChangeType.MODIFIED);
        FileEntry b = new FileEntry("net/vanfleteren/B.java", ChangeType.UNTRACKED);
        Model before = TestModels.loaded(new FixedProvider(List.of(a, b)));
        Model after = TestModels.loaded(new FixedProvider(List.of(new FileEntry("aaa.txt", ChangeType.ADDED), a, b)));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, FilesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(before, FilesPanel.ID));
            assertTrue(screen.contains("net/vanfleteren"), screen);
            assertTrue(screen.contains(" M A.java"), screen);
            assertFalse(screen.contains("net/vanfleteren/A.java"), screen);

            RenderedText.of(testRunner, () -> panel.tree().selected(3));
            RenderedText.of(testRunner, () -> panel.render(after, FilesPanel.ID));
            FileTree selected = panel.tree().selectedNode().data();
            assertEquals(b, ((FileTree.File) selected).entry());

            testRunner.pilot().quit();
        }
    }
}
