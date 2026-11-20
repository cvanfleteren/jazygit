package net.vanfleteren.jazygit.kernel.ui.files;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.ChangeType;
import net.vanfleteren.jazygit.core.git.model.CommitFile;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Loadable;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.state.diff.Changes;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.state.view.CommitFilesView;
import net.vanfleteren.jazygit.kernel.state.view.FileStepMsg.Step;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import org.junit.jupiter.api.Test;

/**
 * The files of a commit opened from a line blamed on it, which start on the file of the line (ADR 147).
 */
class FilesPanelWantedTest {

    private static final Changes COMMIT = new Changes.OfCommit(new Sha("abcdef1234567"));
    private static final CommitFile FIRST = new CommitFile("docs/a.md", ChangeType.MODIFIED);
    private static final CommitFile WANTED = new CommitFile("src/main/B.java", ChangeType.MODIFIED);

    private final FilesPanel panel = new FilesPanel(msg -> {}, () -> {});

    private static Model opened(Option<String> wanted) {
        return TestModels.loaded()
                .mapRepoView(view -> view.mapFiles(files -> files.withCommitFiles(
                        Option.of(CommitFilesView.of(COMMIT, Scopes.COMMITS.pane(), Scopes.FILES.pane())
                                .withFiles(new Loadable.Loaded<>(List.of(FIRST, WANTED)))
                                .withWanted(wanted)))));
    }

    @Test
    void theFileWantedIsHighlightedOnceTheFilesAreRead() throws Exception {
        Model model = opened(Option.of(WANTED.path()));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, FilesPanel.PANE))) {
            RenderedText.ofLarge(testRunner, () -> panel.render(model, FilesPanel.PANE));

            assertEquals(
                    Option.of(new SelectionMsg.CommitFilesSelected(List.of(WANTED))), panel.selectionChange(model));
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void theSameFileOfTheSameCommitOpenedAgainIsHighlightedAgain() throws Exception {
        Model model = opened(Option.of(WANTED.path()));
        // Opened again, the files are read again.
        Model reading = model.mapRepoView(view -> view.mapFiles(
                files -> files.withCommitFiles(files.commitFiles().map(open -> open.withFiles(Loadable.loading())))));
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, FilesPanel.PANE))) {
            RenderedText.ofLarge(testRunner, () -> panel.render(model, FilesPanel.PANE));
            assertTrue(panel.step(model, Step.PREVIOUS));
            assertEquals(Option.of(new SelectionMsg.CommitFilesSelected(List.of(FIRST))), panel.selectionChange(model));

            RenderedText.ofLarge(testRunner, () -> panel.render(reading, FilesPanel.PANE));
            RenderedText.ofLarge(testRunner, () -> panel.render(model, FilesPanel.PANE));

            assertEquals(
                    Option.of(new SelectionMsg.CommitFilesSelected(List.of(WANTED))), panel.selectionChange(model));
            SettledPilot.of(testRunner).quit();
        }
    }

    @Test
    void withoutOneTheyStartOnTheFirstDirectoryWithChanges() throws Exception {
        Model model = opened(Option.none());
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, FilesPanel.PANE))) {
            RenderedText.ofLarge(testRunner, () -> panel.render(model, FilesPanel.PANE));

            assertEquals(Option.of(new SelectionMsg.CommitFilesSelected(List.of(FIRST))), panel.selectionChange(model));
            SettledPilot.of(testRunner).quit();
        }
    }
}
