package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.layout.Size;
import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.MouseButton;
import dev.tamboui.tui.pilot.Pilot;
import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.StubGitProvider;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.git.model.RepoStatus.Head;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import org.junit.jupiter.api.Test;

/**
 * The files panel must follow the model as the working tree status changes.
 */
class FilesPanelTest {

    private final FilesPanel panel = new FilesPanel(msg -> {}, () -> {});

    @Test
    void conflictedFilesShowAMarkerForEachBranchThatChangedThem() throws Exception {
        Model updated = TestModels.loaded(new FixedProvider(List.of(
                FileEntry.conflicted("both.txt", Conflict.of('U', 'U')),
                FileEntry.conflicted("added.txt", Conflict.of('A', 'A')))));
        Model deleted = TestModels.loaded(new FixedProvider(List.of(
                FileEntry.conflicted("theirs-deleted.txt", Conflict.of('U', 'D')),
                FileEntry.conflicted("ours-deleted.txt", Conflict.of('D', 'U')))));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(updated, FilesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(updated, FilesPanel.ID));
            assertTrue(screen.contains("UU both.txt"), screen);
            assertTrue(screen.contains("UU added.txt"), screen);

            String other = RenderedText.of(testRunner, () -> panel.render(deleted, FilesPanel.ID));
            assertTrue(other.contains("UD theirs-deleted.txt"), other);
            assertTrue(other.contains("DU ours-deleted.txt"), other);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void theHighlightedNodeOnlyShowsWhileThePaneIsInUse() throws Exception {
        Model model =
                TestModels.loaded(new FixedProvider(List.of(FileEntry.conflicted("both.txt", Conflict.of('U', 'U')))));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, FilesPanel.ID))) {
            String active = RenderedText.of(testRunner, () -> panel.render(model, FilesPanel.ID));
            assertTrue(active.contains("> "), active);

            String inactive = RenderedText.of(testRunner, () -> panel.render(model, CommitsPanel.ID, CommitsPanel.ID));
            assertFalse(inactive.contains(">"), inactive);
            assertTrue(inactive.contains("UU both.txt"), inactive);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void renamedFilesShowWhereTheyCameFrom() throws Exception {
        Model sameDirectory = TestModels.loaded(new FixedProvider(List.of(
                FileEntry.renamed("b.txt", "a.txt", true, false), FileEntry.renamed("c.txt", "old.txt", true, true))));
        Model otherDirectory =
                TestModels.loaded(new FixedProvider(List.of(FileEntry.renamed("lib/m.txt", "src/m.txt", true, false))));

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(sameDirectory, FilesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(sameDirectory, FilesPanel.ID));
            assertTrue(screen.contains("R  a.txt -> b.txt"), screen);
            assertTrue(screen.contains("RM old.txt -> c.txt"), screen);

            String other = RenderedText.of(testRunner, () -> panel.render(otherDirectory, FilesPanel.ID));
            assertTrue(other.contains("R  src/m.txt -> m.txt"), other);

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void changesOnlyInTheWorkingTreeShowInTheUnstagedColumn() throws Exception {
        // The rename only in the working tree follows intent-to-add of the new path.
        Model model = TestModels.loaded(new FixedProvider(List.of(
                new FileEntry("gone.txt", ChangeType.DELETED), FileEntry.renamed("b.txt", "a.txt", false, true))));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, FilesPanel.ID))) {
            String screen = RenderedText.of(testRunner, () -> panel.render(model, FilesPanel.ID));
            assertTrue(screen.contains(" D gone.txt"), screen);
            assertTrue(screen.contains(" R a.txt -> b.txt"), screen);

            SettledPilot.of(testRunner).quit();
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

            SettledPilot.of(testRunner).quit();
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

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void rendersMergedDirectoriesAndKeepsSelectionWhenStatusChanges() throws Exception {
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

            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void whenTheSelectedFileIsGoneTheNextOneIsSelected() throws Exception {
        assertEquals("c.txt", selectedAfter("b.txt", List.of("a.txt", "b.txt", "c.txt"), List.of("a.txt", "c.txt")));
    }

    @Test
    void whenTheSelectedFileIsGoneAndWasLastThePreviousOneIsSelected() throws Exception {
        assertEquals("b.txt", selectedAfter("c.txt", List.of("a.txt", "b.txt", "c.txt"), List.of("a.txt", "b.txt")));
    }

    @Test
    void whenTheSelectedFileIsGoneItsNeighbourIsSelectedEvenWhenFilesAboveAreGoneToo() throws Exception {
        List<String> names = List.of("a.txt", "b.txt", "c.txt", "d.txt", "e.txt");
        assertEquals("d.txt", selectedAfter("c.txt", names, List.of("b.txt", "d.txt", "e.txt")));
    }

    /**
     * The name of the file selected once only the {@code remaining} files are changed, when {@code selected}
     * was, among the changed {@code names}.
     */
    private String selectedAfter(String selected, List<String> names, List<String> remaining) throws Exception {
        Model before = TestModels.loaded(new FixedProvider(names.map(n -> new FileEntry(n, ChangeType.MODIFIED))));
        Model after = TestModels.loaded(new FixedProvider(remaining.map(n -> new FileEntry(n, ChangeType.MODIFIED))));

        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(before, FilesPanel.ID))) {
            RenderedText.of(testRunner, () -> panel.render(before, FilesPanel.ID));
            // Below the root.
            RenderedText.of(testRunner, () -> panel.tree().selected(names.indexOf(selected) + 1));
            RenderedText.of(testRunner, () -> panel.render(after, FilesPanel.ID));
            String name = ((FileTree.File) panel.tree().selectedNode().data()).name();

            SettledPilot.of(testRunner).quit();
            return name;
        }
    }

    @Test
    void clickingANodeHighlightsIt() throws Exception {
        Model model = TestModels.loaded(new FixedProvider(
                List.of(new FileEntry("a.txt", ChangeType.MODIFIED), new FileEntry("b.txt", ChangeType.MODIFIED))));

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(model, FilesPanel.ID), new Size(40, 8))) {
            Pilot pilot = SettledPilot.of(testRunner);
            // The border, the root, a.txt, then b.txt.
            pilot.mousePress(MouseButton.LEFT, 5, 3);

            assertEquals("b.txt", ((FileTree.File) panel.tree().selectedNode().data()).name());

            pilot.quit();
        }
    }

    @Test
    void clickingANodeOfAScrolledTreeHighlightsTheNodeShownThere() throws Exception {
        Model model = TestModels.loaded(new FixedProvider(
                List.range(0, 20).map(i -> new FileEntry("f%02d.txt".formatted(i), ChangeType.MODIFIED))));

        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(model, FilesPanel.ID), new Size(40, 8))) {
            Pilot pilot = SettledPilot.of(testRunner);
            // Six rows fit inside the border; at the last file they show f14.txt to f19.txt.
            SettledPilot.focus(testRunner, FilesPanel.ID);
            pilot.press(KeyCode.END);
            pilot.mousePress(MouseButton.LEFT, 5, 2);

            assertEquals("f15.txt", ((FileTree.File) panel.tree().selectedNode().data()).name());

            pilot.quit();
        }
    }

    private record FixedProvider(List<FileEntry> files) implements StubGitProvider {

        @Override
        public Either<ProcessError, RepoStatus> status() {
            return Either.right(new RepoStatus(Head.onBranch(new Branch.Name("main"), new Sha("aaaa")), files));
        }
    }
}
