package net.vanfleteren.jazygit.ui.files;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.FileTree;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.load.SelectionMsg;
import net.vanfleteren.jazygit.state.view.FileFilter;
import net.vanfleteren.jazygit.state.view.FileStepMsg.Step;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * Moving the highlight of the files pane to the next and the previous file from the content panel (ADR 117): through
 * every file of the tree, those in a collapsed directory too, which opens.
 */
class FilesPanelStepTest {

    private static final FileEntry CHANGED = new FileEntry("src/main/A.java", ChangeType.MODIFIED, false, true);
    // All nodes, depth first: /, docs/guide, intro.md, src/main, A.java, B.java, README.md. docs/guide starts
    // collapsed, as it holds no changes.
    private static final List<String> ALL =
            List.of("README.md", "docs/guide/intro.md", "src/main/A.java", "src/main/B.java");

    private final FilesPanel panel = new FilesPanel(msg -> {}, () -> {});

    private static Model all() {
        Model loaded = TestModels.loaded();
        return loaded.mapRepo(repo -> repo.withStatus(repo.status().map(status -> status.withFiles(List.of(CHANGED))))
                        .withAllFiles(new Loadable.Loaded<>(ALL)))
                .mapRepoView(view -> view.withAllFiles(true));
    }

    @Test
    void theNextFileIsTheFirstAfterTheNodeAndThePreviousTheLastBeforeIt() {
        List<FileTree<FileEntry>> nodes =
                List.of(dir(""), dir("docs"), file("docs/a"), dir("src"), file("src/b"), file("src/c"));

        assertEquals(Option.of(2), FileTreeList.stepped(nodes, 0, Step.NEXT));
        // A directory's first file comes after it.
        assertEquals(Option.of(2), FileTreeList.stepped(nodes, 1, Step.NEXT));
        assertEquals(Option.of(4), FileTreeList.stepped(nodes, 2, Step.NEXT));
        assertEquals(Option.of(5), FileTreeList.stepped(nodes, 4, Step.NEXT));
        assertEquals(Option.none(), FileTreeList.stepped(nodes, 5, Step.NEXT));
        // With nothing highlighted, the first file.
        assertEquals(Option.of(2), FileTreeList.stepped(nodes, -1, Step.NEXT));

        assertEquals(Option.of(4), FileTreeList.stepped(nodes, 5, Step.PREVIOUS));
        // Past the directory of a file, to the file before it.
        assertEquals(Option.of(2), FileTreeList.stepped(nodes, 4, Step.PREVIOUS));
        assertEquals(Option.of(2), FileTreeList.stepped(nodes, 3, Step.PREVIOUS));
        assertEquals(Option.none(), FileTreeList.stepped(nodes, 2, Step.PREVIOUS));
        assertEquals(Option.none(), FileTreeList.stepped(nodes, 0, Step.PREVIOUS));
        assertEquals(Option.none(), FileTreeList.stepped(nodes, -1, Step.PREVIOUS));
    }

    @Test
    void aStepGoesIntoACollapsedDirectoryWhichOpensAndOnThroughTheFilesWithoutChanges() throws Exception {
        Model model = all();
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, FilesPanel.ID))) {
            String before = RenderedText.ofLarge(testRunner, () -> panel.render(model, FilesPanel.ID));
            assertFalse(before.contains("intro.md"), before);

            assertTrue(panel.step(model, Step.NEXT));
            assertEquals(
                    Option.of(new SelectionMsg.UnchangedFileSelected("docs/guide/intro.md")),
                    panel.selectionChange(model));
            String opened = RenderedText.ofLarge(testRunner, () -> panel.render(model, FilesPanel.ID));
            assertTrue(opened.contains("intro.md"), opened);

            assertTrue(panel.step(model, Step.NEXT));
            assertEquals(Option.of(new SelectionMsg.FilesSelected(List.of(CHANGED))), panel.selectionChange(model));
            assertTrue(panel.step(model, Step.NEXT));
            assertEquals(
                    Option.of(new SelectionMsg.UnchangedFileSelected("src/main/B.java")), panel.selectionChange(model));
            assertTrue(panel.step(model, Step.NEXT));
            assertEquals(Option.of(new SelectionMsg.UnchangedFileSelected("README.md")), panel.selectionChange(model));

            assertFalse(panel.step(model, Step.NEXT));
            assertEquals(Option.of(new SelectionMsg.UnchangedFileSelected("README.md")), panel.selectionChange(model));

            assertTrue(panel.step(model, Step.PREVIOUS));
            assertEquals(
                    Option.of(new SelectionMsg.UnchangedFileSelected("src/main/B.java")), panel.selectionChange(model));
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void theFilesTheFilterHidesAreSkipped() throws Exception {
        Model filtered =
                all().mapRepoView(view -> view.withFilesFilter(Option.of(new FileFilter("main", Option.none()))));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(filtered, FilesPanel.ID))) {
            RenderedText.ofLarge(testRunner, () -> panel.render(filtered, FilesPanel.ID));

            assertTrue(panel.step(filtered, Step.NEXT));
            assertEquals(Option.of(new SelectionMsg.FilesSelected(List.of(CHANGED))), panel.selectionChange(filtered));
            assertTrue(panel.step(filtered, Step.NEXT));
            assertEquals(
                    Option.of(new SelectionMsg.UnchangedFileSelected("src/main/B.java")),
                    panel.selectionChange(filtered));
            assertFalse(panel.step(filtered, Step.NEXT));
            SettledPilot.of(testRunner).quit();
        }
    }

    private static FileTree<FileEntry> dir(String path) {
        return new FileTree.Dir<>(path, path, List.empty());
    }

    private static FileTree<FileEntry> file(String path) {
        return new FileTree.File<>(
                path.substring(path.lastIndexOf('/') + 1), new FileEntry(path, ChangeType.MODIFIED, false, true));
    }
}
