package net.vanfleteren.jazygit.features.changes.panes.files;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.ChangeType;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.ui.Fills;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import org.junit.jupiter.api.Test;

/**
 * What the features fill in the files pane, which knows none of them (ADR 172): the marks after a file of the working
 * tree, and what its title says after what it is.
 */
class FilesPaneSlotsTest {

    private static final FileEntry A = new FileEntry("a.txt", ChangeType.MODIFIED, true, false);
    private static final FileEntry B = new FileEntry("b.txt", ChangeType.MODIFIED, true, false);

    private static final Model MODEL = TestModels.loaded()
            .mapRepo(repo -> repo.withStatus(repo.status().map(status -> status.withFiles(List.of(A, B)))));

    // Marks a.txt, and only it.
    private static final FileMark ON_A = file -> Option.when(
            file.path().equals("a.txt"), () -> new FileMark.Mark(List.of(LoadableList.Seg.of(" ★")), false));
    private static final FileMark ON_A_TOO = file -> Option.when(
            file.path().equals("a.txt"), () -> new FileMark.Mark(List.of(LoadableList.Seg.of(" ☆")), false));

    @Test
    void theMarksOfAllFeaturesFollowTheNameOfTheFileTheyMarkInTheOrderOfTheFeatures() throws Exception {
        Fills fills = Fills.NONE
                .with(FilesPane.MARK, model -> Option.of(ON_A))
                .with(FilesPane.MARK, model -> Option.of(ON_A_TOO));

        String screen = render(fills);

        assertTrue(screen.contains("a.txt ★ ☆"), screen);
        assertFalse(screen.contains("b.txt ★"), screen);
    }

    @Test
    void theTitleSaysWhatEachFeatureGivesForItAfterWhatThePaneIs() throws Exception {
        Fills fills = Fills.NONE
                .with(FilesPane.TITLE, model -> Option.of(" · one"))
                .with(FilesPane.TITLE, model -> List.of(" · two"));

        assertTrue(render(fills).contains("Files · one · two"));
    }

    @Test
    void withNothingFilledTheFilesShowAsGitSaysThem() throws Exception {
        String screen = render(Fills.NONE);

        assertTrue(screen.contains("Files"), screen);
        assertFalse(screen.contains("★"), screen);
    }

    private static String render(Fills fills) throws Exception {
        FilesPanel panel = new FilesPanel(msg -> {}, () -> {}, Theme.Highlights.DEFAULT, fills);
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(MODEL, FilesPanel.PANE))) {
            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(MODEL, FilesPanel.PANE));
            SettledPilot.of(testRunner).quit();
            return screen;
        }
    }
}
