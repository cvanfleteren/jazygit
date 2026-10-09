package net.vanfleteren.jazygit.ui;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.pilot.Pilot;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The left panes show the position of the selected row on their bottom border.
 */
class PanePositionTest {

    private record FixedProvider(List<FileEntry> files, List<Branch> branches, List<Commit> commits)
            implements GitInfoProvider {

        @Override
        public String repositoryName() {
            return "repo";
        }

        @Override
        public RepoStatus status() {
            return new RepoStatus("main", "aaaa", files);
        }

        @Override
        public List<Commit> log(String branch) {
            return commits;
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
        public void amendLastCommit() {
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
        public Diffs diff(List<FileEntry> files) {
            throw new UnsupportedOperationException();
        }
    }

    private static final Model MODEL = TestModels.loaded(new FixedProvider(
            List.of(new FileEntry("net/vanfleteren/A.java", ChangeType.MODIFIED),
                    new FileEntry("net/vanfleteren/B.java", ChangeType.UNTRACKED)),
            List.of(new Branch("main", true, "aaaa"), new Branch("feature", false, "bbbb")),
            List.of(new Commit("aaaa", "me", "me@example.com", Instant.EPOCH, "first", ""),
                    new Commit("bbbb", "me", "me@example.com", Instant.EPOCH, "second", ""),
                    new Commit("cccc", "me", "me@example.com", Instant.EPOCH, "third", ""))));

    @Test
    void branchesShowSelectedPositionAndFollowSelection() throws Exception {
        BranchesPanel panel = new BranchesPanel(msg -> { });
        assertPositions(() -> panel.render(MODEL, BranchesPanel.ID), BranchesPanel.ID, KeyCode.DOWN, "1/2", "2/2");
    }

    @Test
    void commitsShowSelectedPositionAndFollowSelection() throws Exception {
        CommitsPanel panel = new CommitsPanel();
        assertPositions(() -> panel.render(MODEL, CommitsPanel.ID), CommitsPanel.ID, KeyCode.DOWN, "1/3", "2/3");
    }

    @Test
    void filesCountOnlyVisibleRows() throws Exception {
        FilesPanel panel = new FilesPanel(msg -> { });
        // The root, the merged directory and its two files; collapsing the directory hides the files.
        assertPositions(() -> panel.render(MODEL, FilesPanel.ID), FilesPanel.ID, KeyCode.LEFT, "1/4", "1/1");
    }

    @Test
    void showsNoPositionWhileLoading() throws Exception {
        BranchesPanel panel = new BranchesPanel(msg -> { });
        Model loading = TestModels.loading("repo");
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(loading, BranchesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(loading, BranchesPanel.ID));
            assertTrue(screen.contains(Placeholders.LOADING), screen);
            assertFalse(screen.contains("1/1"), screen);

            testRunner.pilot().quit();
        }
    }

    private static void assertPositions(Supplier<Element> render, String id, KeyCode key,
                                        String before, String after) throws Exception {
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(render)) {
            testRunner.runner().focusManager().setFocus(id);
            Pilot pilot = testRunner.pilot();
            pilot.pause();

            String initial = RenderedText.of(testRunner, render);
            assertTrue(bottomBorder(initial).contains(before), initial);

            pilot.press(key);
            pilot.pause();
            String moved = RenderedText.of(testRunner, render);
            assertTrue(bottomBorder(moved).contains(after), moved);

            pilot.quit();
        }
    }

    private static String bottomBorder(String screen) {
        return screen.lines().reduce((first, second) -> second).orElse("");
    }
}
