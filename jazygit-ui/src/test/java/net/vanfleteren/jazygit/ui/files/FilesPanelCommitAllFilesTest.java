package net.vanfleteren.jazygit.ui.files;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.state.load.SelectionMsg;
import net.vanfleteren.jazygit.state.view.CommitFilesView;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.keys.Scopes;
import net.vanfleteren.jazygit.ui.text.Placeholders;
import org.junit.jupiter.api.Test;

/**
 * The files pane listing all files of a commit (ADR 128): the files it did not change among those it changed, with no
 * marker, opening only on the changes, and the content panel told what a node without changes is.
 */
class FilesPanelCommitAllFilesTest {

    private static final Changes TIP = new Changes.OfTip(new BranchName("main"), new Sha("abcdef1234567"));
    private static final CommitFile CHANGED = new CommitFile("src/main/A.java", ChangeType.MODIFIED);
    // Rows: /, docs/guide, src/main, M A.java, B.java, README.md: directories come first.
    private static final List<CommitFile> ALL = List.of(
            new CommitFile("README.md", ChangeType.UNCHANGED),
            new CommitFile("docs/guide/intro.md", ChangeType.UNCHANGED),
            CHANGED,
            new CommitFile("src/main/B.java", ChangeType.UNCHANGED));

    private final FilesPanel panel = new FilesPanel(msg -> {}, () -> {});

    private static Model listing(boolean all, Loadable<List<CommitFile>> tree) {
        return TestModels.loaded()
                .mapRepoView(view -> view.mapFiles(files -> files.withCommitFiles(
                        Option.of(CommitFilesView.of(TIP, Scopes.BRANCHES.pane(), Scopes.FILES.pane())
                                .withFiles(new Loadable.Loaded<>(List.of(CHANGED)))
                                .withAllFiles(all)
                                .withTree(Option.of(tree))))));
    }

    @Test
    void allFilesAreListedThoseUnchangedWithoutAMarkerAndCollapsedUnlessTheyHoldChanges() throws Exception {
        Model model = listing(true, new Loadable.Loaded<>(ALL));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, FilesPanel.PANE))) {
            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(model, FilesPanel.PANE));

            assertTrue(screen.contains("Files of main · abcdef1 · all files"), screen);
            assertTrue(screen.contains("M A.java"), screen);
            assertTrue(screen.contains("  B.java"), screen);
            assertTrue(screen.contains("README.md"), screen);
            assertTrue(screen.contains("docs/guide"), screen);
            assertFalse(screen.contains("intro.md"), screen);
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void theChangedFilesKeepTheirOwnTree() throws Exception {
        Model model = listing(false, new Loadable.Loaded<>(ALL));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, FilesPanel.PANE))) {
            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(model, FilesPanel.PANE));

            assertFalse(screen.contains("all files"), screen);
            assertFalse(screen.contains("README.md"), screen);
            assertTrue(screen.contains("M A.java"), screen);
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void aNodeWithoutChangesIsReadOrCountedAndOneWithChangesShowsItsDiff() throws Exception {
        Model model = listing(true, new Loadable.Loaded<>(ALL));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, FilesPanel.PANE))) {
            RenderedText.ofLarge(testRunner, () -> panel.render(model, FilesPanel.PANE));

            panel.commitAllTree().selected(5);
            assertEquals(Option.of(new SelectionMsg.UnchangedFileSelected("README.md")), panel.selectionChange(model));

            panel.commitAllTree().selected(1);
            assertEquals(
                    Option.of(new SelectionMsg.UnchangedDirectorySelected("docs/guide", 1)),
                    panel.selectionChange(model));

            panel.commitAllTree().selected(2);
            // Its diff is of its changed files only, as without all files.
            assertEquals(
                    Option.of(new SelectionMsg.CommitFilesSelected(List.of(CHANGED))), panel.selectionChange(model));
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void untilAllFilesAreReadTheTreeSaysItIsLoading() throws Exception {
        Model model = listing(true, Loadable.loading());
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, FilesPanel.PANE))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(model, FilesPanel.PANE));

            assertTrue(screen.contains(Placeholders.loading()), screen);
            SettledPilot.of(testRunner).quit();
        }
    }
}
