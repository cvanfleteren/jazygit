package net.vanfleteren.jazygit.features.changes.panes.files;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.ChangeType;
import net.vanfleteren.jazygit.core.git.model.CommitFile;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RepoPath;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.view.CommitFilesView;
import net.vanfleteren.jazygit.kernel.state.view.FilesShown;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import org.junit.jupiter.api.Test;

/**
 * Switching the files pane between the changed files and all files with {@code a} keeps the highlight on its path:
 * a node among the changed files is among all files too; one without changes gives it to the deepest directory with
 * changes that holds it.
 */
class FilesPanelListSwitchTest {

    private static final Changes TIP = new Changes.OfTip(new BranchName("main"), new Sha("abcdef1234567"));
    private static final Changes OTHER = new Changes.OfCommit(new Sha("1234567abcdef"));
    private static final CommitFile CHANGED = new CommitFile(new RepoPath("src/main/A.java"), ChangeType.MODIFIED);
    // Changed rows: /, src/main, M A.java.
    // All rows: /, docs/guide, src/main, M A.java, B.java, README.md: directories come first.
    private static final List<CommitFile> ALL = List.of(
            new CommitFile(new RepoPath("README.md"), ChangeType.UNCHANGED),
            new CommitFile(new RepoPath("docs/guide/intro.md"), ChangeType.UNCHANGED),
            CHANGED,
            new CommitFile(new RepoPath("src/main/B.java"), ChangeType.UNCHANGED));

    private final FilesPanel panel = new FilesPanel(msg -> {}, () -> {});

    private static Model commit(Changes of, boolean all, Loadable<List<CommitFile>> tree) {
        return TestModels.loaded()
                .mapRepoView(view -> view.mapFiles(files -> files.withCommitFiles(
                        Option.of(CommitFilesView.of(of, Scopes.BRANCHES.pane(), Scopes.FILES.pane())
                                .withFiles(new Loadable.Loaded<>(List.of(CHANGED)))
                                .withAllFiles(all)
                                .withTree(Option.of(tree))))));
    }

    private static Model working(FilesShown shown) {
        return TestModels.loaded()
                .mapRepo(repo -> repo.withStatus(repo.status()
                                .map(status -> status.withFiles(List.of(new FileEntry(
                                        new RepoPath("src/main/A.java"), ChangeType.MODIFIED, false, true)))))
                        .withAllFiles(new Loadable.Loaded<>(ALL.map(CommitFile::path))))
                .mapRepoView(view -> view.mapFiles(files -> files.withShown(shown)));
    }

    private void draw(ToolkitTestRunner testRunner, Model model) throws Exception {
        RenderedText.ofLarge(testRunner, () -> panel.render(model, FilesPanel.PANE));
    }

    @Test
    void aChangedFileOfACommitStaysHighlightedAmongAllItsFiles() throws Exception {
        Model changed = commit(TIP, false, Loadable.loading());
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(changed, FilesPanel.PANE))) {
            draw(testRunner, changed);
            panel.commitTree().selected(2);

            draw(testRunner, commit(TIP, true, new Loadable.Loaded<>(ALL)));

            assertEquals(3, panel.commitAllTree().selected());
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void aDirectoryOfACommitStaysHighlightedOnceAllItsFilesAreRead() throws Exception {
        Model changed = commit(TIP, false, Loadable.loading());
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(changed, FilesPanel.PANE))) {
            draw(testRunner, changed);
            panel.commitTree().selected(1);

            draw(testRunner, commit(TIP, true, Loadable.loading()));
            draw(testRunner, commit(TIP, true, new Loadable.Loaded<>(ALL)));

            assertEquals(2, panel.commitAllTree().selected());
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void backAmongTheChangedFilesTheHighlightStaysOnItsPathOrGoesToTheDirectoryHoldingIt() throws Exception {
        Model all = commit(TIP, true, new Loadable.Loaded<>(ALL));
        Model changed = commit(TIP, false, new Loadable.Loaded<>(ALL));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(all, FilesPanel.PANE))) {
            draw(testRunner, all);
            panel.commitAllTree().selected(3);
            draw(testRunner, changed);
            assertEquals(2, panel.commitTree().selected(), "M A.java");

            draw(testRunner, all);
            panel.commitAllTree().selected(4);
            draw(testRunner, changed);
            assertEquals(1, panel.commitTree().selected(), "B.java goes to src/main");

            draw(testRunner, all);
            panel.commitAllTree().selected(5);
            draw(testRunner, changed);
            assertEquals(0, panel.commitTree().selected(), "README.md goes to the root");
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void theFilesOfAnotherCommitStartAfresh() throws Exception {
        Model all = commit(TIP, true, new Loadable.Loaded<>(ALL));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(all, FilesPanel.PANE))) {
            draw(testRunner, all);
            panel.commitAllTree().selected(3);

            draw(testRunner, commit(OTHER, false, Loadable.loading()));
            draw(testRunner, commit(OTHER, false, new Loadable.Loaded<>(ALL)));

            // The first directory with changes, as the files of a commit start on.
            assertEquals(1, panel.commitTree().selected());
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void aPathCarriedToFilesNeverReadIsNotCarriedToTheFilesOfAnotherCommit() throws Exception {
        Model changed = commit(TIP, false, Loadable.loading());
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(changed, FilesPanel.PANE))) {
            draw(testRunner, changed);
            panel.commitTree().selected(2);
            draw(testRunner, commit(TIP, true, Loadable.loading()));

            // As the main branch's files, which list all files from the start.
            draw(testRunner, commit(OTHER, true, Loadable.loading()));
            draw(testRunner, commit(OTHER, true, new Loadable.Loaded<>(ALL)));

            // The root, which holds README.md directly, as all files of a commit start on.
            assertEquals(0, panel.commitAllTree().selected());
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void aDirectoryMergedIntoANodeGoesToThatNodeAndOneWithoutChangesToTheDirectoryHoldingIt() throws Exception {
        // All rows: /, docs/guide, src, main, M A.java, B.java, test, README.md; src/test starts collapsed.
        // Changed rows: /, src/main, M A.java: src is merged with its only child directory there.
        Loadable<List<CommitFile>> tree = new Loadable.Loaded<>(
                ALL.append(new CommitFile(new RepoPath("src/test/C.java"), ChangeType.UNCHANGED)));
        Model all = commit(TIP, true, tree);
        Model changed = commit(TIP, false, tree);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(all, FilesPanel.PANE))) {
            draw(testRunner, all);
            panel.commitAllTree().selected(2);
            draw(testRunner, changed);
            assertEquals(1, panel.commitTree().selected(), "src goes to src/main");

            draw(testRunner, all);
            panel.commitAllTree().selected(1);
            draw(testRunner, changed);
            assertEquals(0, panel.commitTree().selected(), "docs/guide goes to the root");
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void theHighlightOfTheWorkingTreeStaysOnItsPathBothWays() throws Exception {
        Model changed = working(FilesShown.CHANGED);
        Model all = working(FilesShown.ALL);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(changed, FilesPanel.PANE))) {
            draw(testRunner, changed);
            panel.tree().selected(2);
            draw(testRunner, all);
            assertEquals(3, panel.allTree().selected(), "M A.java");

            panel.allTree().selected(4);
            draw(testRunner, changed);
            assertEquals(1, panel.tree().selected(), "B.java goes to src/main");
            SettledPilot.of(testRunner).quit();
        }
    }
}
