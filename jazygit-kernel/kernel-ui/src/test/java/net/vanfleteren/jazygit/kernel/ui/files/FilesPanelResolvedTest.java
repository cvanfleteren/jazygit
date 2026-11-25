package net.vanfleteren.jazygit.kernel.ui.files;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.ChangeType;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.FileEntry;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.InProgress;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import net.vanfleteren.jazygit.kernel.ui.RenderedText;
import net.vanfleteren.jazygit.kernel.ui.SettledPilot;
import org.junit.jupiter.api.Test;

/**
 * The files pane while an operation is stopped on conflicts: how many are resolved, and which (ADR 198).
 */
class FilesPanelResolvedTest {

    private static final InProgress REBASING =
            new InProgress.Rebasing(Option.of(new BranchName("feature")), new Sha("aaaa"), 2, 3);
    private static final FileEntry CONFLICTED =
            FileEntry.conflicted("a.txt", new Conflict(Conflict.Side.UPDATED, Conflict.Side.UPDATED));
    private static final FileEntry RESOLVED = new FileEntry("b.txt", ChangeType.MODIFIED, true, false);
    private static final FileEntry OTHER = new FileEntry("c.txt", ChangeType.MODIFIED, true, false);

    private final FilesPanel panel = new FilesPanel(msg -> {}, () -> {});

    private static Model stopped(List<String> resolved) {
        return TestModels.loaded()
                .mapRepo(repo -> repo.withStatus(repo.status()
                        .map(status -> status.withFiles(List.of(CONFLICTED, RESOLVED, OTHER))
                                .withInProgress(Option.of(REBASING))
                                .withResolved(resolved))));
    }

    @Test
    void theTitleSaysHowManyOfTheConflictsAreResolvedAndTheirRowsAreMarked() throws Exception {
        String screen = render(stopped(List.of("b.txt")));

        assertTrue(screen.contains("Files · 1 of 2 resolved"), screen);
        assertTrue(screen.contains("b.txt ✓"), screen);
        assertFalse(screen.contains("c.txt ✓"), screen);
        assertFalse(screen.contains("a.txt ✓"), screen);
    }

    @Test
    void beforeAnyIsResolvedTheTitleSaysNothingOfIt() throws Exception {
        String screen = render(stopped(List.empty()));

        assertFalse(screen.contains("resolved"), screen);
    }

    private String render(Model model) throws Exception {
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, FilesPanel.PANE))) {
            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(model, FilesPanel.PANE));
            SettledPilot.of(testRunner).quit();
            return screen;
        }
    }
}
