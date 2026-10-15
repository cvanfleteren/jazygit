package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.pilot.Pilot;
import io.vavr.collection.List;
import io.vavr.control.Either;
import java.time.Instant;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.git.GitError.LogError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.RepoStatus;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import org.junit.jupiter.api.Test;

/**
 * The left panes show the position of the selected row on their bottom border.
 */
class PanePositionTest {

    private record FixedProvider(List<FileEntry> files, List<Branch> branchList, List<Commit> commitList)
            implements StubGitProvider {

        @Override
        public Either<ProcessError, RepoStatus> status() {
            return Either.right(new RepoStatus(Head.onBranch(new Branch.Name("main"), new Sha("aaaa")), files));
        }

        @Override
        public Either<ProcessError, List<Branch>> branches() {
            return Either.right(branchList);
        }

        @Override
        public Either<ProcessError, List<Commit>> commits() {
            return Either.right(commitList);
        }

        @Override
        public Either<LogError, List<Commit>> log(BranchRef branch) {
            return Either.right(commitList);
        }
    }

    private static final Model MODEL = TestModels.loaded(new FixedProvider(
            List.of(
                    new FileEntry("net/vanfleteren/A.java", ChangeType.MODIFIED),
                    new FileEntry("net/vanfleteren/B.java", ChangeType.UNTRACKED)),
            List.of(
                    new Branch(new Branch.Name("main"), true, new Sha("aaaa")),
                    new Branch(new Branch.Name("feature"), false, new Sha("bbbb"))),
            List.of(
                    new Commit(new Sha("aaaa"), "me", "me@example.com", Instant.EPOCH, "first", ""),
                    new Commit(new Sha("bbbb"), "me", "me@example.com", Instant.EPOCH, "second", ""),
                    new Commit(new Sha("cccc"), "me", "me@example.com", Instant.EPOCH, "third", ""))));

    @Test
    void branchesShowSelectedPositionAndFollowSelection() throws Exception {
        BranchesPanel panel = new BranchesPanel(msg -> {}, tab -> {});
        assertPositions(() -> panel.render(MODEL, BranchesPanel.ID), BranchesPanel.ID, KeyCode.DOWN, "1/2", "2/2");
    }

    @Test
    void commitsShowSelectedPositionAndFollowSelection() throws Exception {
        CommitsPanel panel = new CommitsPanel(msg -> {}, () -> {});
        assertPositions(() -> panel.render(MODEL, CommitsPanel.ID), CommitsPanel.ID, KeyCode.DOWN, "1/3", "2/3");
    }

    @Test
    void filesCountOnlyVisibleRows() throws Exception {
        FilesPanel panel = new FilesPanel(msg -> {}, () -> {});
        // The root, the merged directory and its two files; collapsing the directory hides the files.
        assertPositions(() -> panel.render(MODEL, FilesPanel.ID), FilesPanel.ID, KeyCode.LEFT, "1/4", "1/1");
    }

    @Test
    void showsNoPositionWhileLoading() throws Exception {
        BranchesPanel panel = new BranchesPanel(msg -> {}, tab -> {});
        Model loading = TestModels.loading("repo");
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(loading, BranchesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(loading, BranchesPanel.ID));
            assertTrue(screen.contains(Placeholders.loading()), screen);
            assertFalse(screen.contains("1/1"), screen);

            testRunner.pilot().quit();
        }
    }

    private static void assertPositions(Supplier<Element> render, String id, KeyCode key, String before, String after)
            throws Exception {
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
