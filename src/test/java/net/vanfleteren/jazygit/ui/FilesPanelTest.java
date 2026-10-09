package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import net.vanfleteren.jazygit.model.Branch;
import net.vanfleteren.jazygit.model.ChangeType;
import net.vanfleteren.jazygit.model.Commit;
import net.vanfleteren.jazygit.model.FileEntry;
import net.vanfleteren.jazygit.model.FileTree;
import net.vanfleteren.jazygit.model.GitInfoProvider;
import net.vanfleteren.jazygit.model.RepoStatus;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The files panel must follow the model as the working tree status changes.
 */
class FilesPanelTest {

    private record FixedProvider(List<FileEntry> files) implements GitInfoProvider {

        @Override
        public String repositoryName() {
            return "repo";
        }

        @Override
        public RepoStatus status() {
            return new RepoStatus("main", "aaaa", files);
        }

        @Override
        public List<Branch> branches() {
            return List.of();
        }

        @Override
        public List<Commit> commits() {
            return List.of();
        }

        @Override
        public List<Commit> log(String branch) {
            return List.of();
        }

        @Override
        public void checkout(String branch) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteBranch(String name, boolean local, boolean remote) {
        }

        @Override
        public void createBranch(String name, String startPoint) {
        }

        @Override
        public void stage(List<String> paths) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void unstageNew(List<String> paths) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reword(String summary, String description) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void amend() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void commit(String summary, String description) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void unstage(List<String> paths) {
            throw new UnsupportedOperationException();
        }

        @Override
        public net.vanfleteren.jazygit.model.Diffs diff(List<FileEntry> files) {
            throw new UnsupportedOperationException();
        }
    }

    private final FilesPanel panel = new FilesPanel(msg -> { });

    @Test
    void rendersLoadingThenStatusChanges() throws Exception {
        Model untracked = TestModels.loaded(new FixedProvider(List.of(new FileEntry("a.txt", ChangeType.UNTRACKED))));
        Model added = TestModels.loaded(new FixedProvider(List.of(new FileEntry("a.txt", ChangeType.ADDED))));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(untracked, FilesPanel.ID))) {
            String loading = RenderedText.of(testRunner, () -> panel.render(TestModels.loading("repo"), FilesPanel.ID));
            assertTrue(loading.contains(Placeholders.LOADING), loading);

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
            assertFalse(screen.contains("/"), screen);

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
