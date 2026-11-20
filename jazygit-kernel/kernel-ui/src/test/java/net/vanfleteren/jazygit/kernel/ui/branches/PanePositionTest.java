package net.vanfleteren.jazygit.kernel.ui.branches;

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
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.StubGit;
import net.vanfleteren.jazygit.core.git.branches.Branches;
import net.vanfleteren.jazygit.core.git.branches.StubBranches;
import net.vanfleteren.jazygit.core.git.files.Status;
import net.vanfleteren.jazygit.core.git.files.StubStatus;
import net.vanfleteren.jazygit.core.git.history.History;
import net.vanfleteren.jazygit.core.git.history.StubHistory;
import net.vanfleteren.jazygit.core.git.model.ChangeType;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.git.model.Fixtures;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Head;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.files.FilesPanel;
import net.vanfleteren.jazygit.kernel.ui.text.Placeholders;
import org.junit.jupiter.api.Test;

/**
 * The left panes show the position of the selected row on their bottom border.
 */
class PanePositionTest {

    private static Git fixed(List<FileEntry> files, List<Branch> branchList, List<Commit> commitList) {
        return new FixedRepo(files, branchList, commitList).git();
    }

    private record FixedRepo(List<FileEntry> files, List<Branch> branchList, List<Commit> commitList) {

        public Status status() {
            return new StubStatus() {
                @Override
                public Either<GitError, RepoStatus> read() {
                    return Either.right(new RepoStatus(Head.onBranch(new BranchName("main"), new Sha("aaaa")), files));
                }
            };
        }

        public Branches branches() {
            return new StubBranches() {
                @Override
                public Either<GitError, List<Branch>> local() {
                    return Either.right(branchList);
                }
            };
        }

        public History history() {
            return new StubHistory() {
                @Override
                public Either<GitError, List<Commit>> commits(Sha head) {
                    return Either.right(commitList);
                }

                @Override
                public Either<GitError, List<Commit>> log(BranchRef branch) {
                    return Either.right(commitList);
                }
            };
        }

        Git git() {
            return StubGit.of().withStatus(status()).withBranches(branches()).withHistory(history());
        }
    }

    private static final Model MODEL = TestModels.loaded(fixed(
            List.of(
                    Fixtures.fileEntry("net/vanfleteren/A.java", ChangeType.MODIFIED),
                    Fixtures.fileEntry("net/vanfleteren/B.java", ChangeType.UNTRACKED)),
            List.of(
                    Fixtures.branch(new BranchName("main"), true, new Sha("aaaa")),
                    Fixtures.branch(new BranchName("feature"), false, new Sha("bbbb"))),
            List.of(
                    Fixtures.commit(new Sha("aaaa"), "me", "me@example.com", Instant.EPOCH, "first", ""),
                    Fixtures.commit(new Sha("bbbb"), "me", "me@example.com", Instant.EPOCH, "second", ""),
                    Fixtures.commit(new Sha("cccc"), "me", "me@example.com", Instant.EPOCH, "third", ""))));

    @Test
    void branchesShowSelectedPositionAndFollowSelection() throws Exception {
        BranchesPanel panel = new BranchesPanel(msg -> {}, tab -> {});
        assertPositions(() -> panel.render(MODEL, BranchesPanel.PANE), BranchesPanel.ID, KeyCode.DOWN, "1/2", "2/2");
    }

    @Test
    void filesCountOnlyVisibleRows() throws Exception {
        FilesPanel panel = new FilesPanel(msg -> {}, () -> {});
        // The root, the merged directory and its two files; collapsing the directory hides the files.
        assertPositions(() -> panel.render(MODEL, FilesPanel.PANE), FilesPanel.ID, KeyCode.LEFT, "1/4", "1/1");
    }

    @Test
    void showsNoPositionWhileLoading() throws Exception {
        BranchesPanel panel = new BranchesPanel(msg -> {}, tab -> {});
        Model loading = TestModels.loading("repo");
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(loading, BranchesPanel.PANE))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(loading, BranchesPanel.PANE));
            assertTrue(screen.contains(Placeholders.loading()), screen);
            assertFalse(screen.contains("1/1"), screen);

            SettledPilot.of(testRunner).quit();
        }
    }

    private static void assertPositions(Supplier<Element> render, String id, KeyCode key, String before, String after)
            throws Exception {
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(render)) {
            SettledPilot.focus(testRunner, id);
            Pilot pilot = SettledPilot.of(testRunner);
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
