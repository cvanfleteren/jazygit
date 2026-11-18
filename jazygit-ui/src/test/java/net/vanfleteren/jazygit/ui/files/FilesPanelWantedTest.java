package net.vanfleteren.jazygit.ui.files;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
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
    void withoutOneTheyStartOnTheFirstDirectoryWithChanges() throws Exception {
        Model model = opened(Option.none());
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, FilesPanel.PANE))) {
            RenderedText.ofLarge(testRunner, () -> panel.render(model, FilesPanel.PANE));

            assertEquals(Option.of(new SelectionMsg.CommitFilesSelected(List.of(FIRST))), panel.selectionChange(model));
            SettledPilot.of(testRunner).quit();
        }
    }
}
