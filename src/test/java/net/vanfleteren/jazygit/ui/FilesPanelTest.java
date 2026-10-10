package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.DiscardPlan;
import net.vanfleteren.jazygit.git.model.Conflict;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.FileTree;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.RepoStatus;
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
        public List<String> checkout(String branch) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> checkoutWithStash(String branch) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> push(String branch, boolean forceWithLease) {
            return List.of();
        }

        @Override
        public List<String> deleteBranch(String name, boolean local, boolean remote) {
            return List.of();
        }

        @Override
        public List<String> createBranch(String name, String startPoint) {
            return List.of();
        }

        @Override
        public List<String> stage(List<String> paths) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> unstageNew(List<String> paths) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> reword(String summary, String description) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> amendLastCommit() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> commit(String summary, String description) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> discard(DiscardPlan plan) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> unstage(List<String> paths) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String commitChanges(String sha) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Diffs diff(List<FileEntry> files) {
            throw new UnsupportedOperationException();
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
