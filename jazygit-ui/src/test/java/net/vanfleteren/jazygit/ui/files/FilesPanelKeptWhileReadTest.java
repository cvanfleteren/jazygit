package net.vanfleteren.jazygit.ui.files;

import static org.junit.jupiter.api.Assertions.*;

import dev.tamboui.toolkit.app.ToolkitTestRunner;
import dev.tamboui.tui.event.KeyCode;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.PaneId;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.state.load.SelectionMsg;
import net.vanfleteren.jazygit.state.view.CommitFilesView;
import net.vanfleteren.jazygit.ui.RenderedText;
import net.vanfleteren.jazygit.ui.SettledPilot;
import net.vanfleteren.jazygit.ui.keys.Scopes;
import net.vanfleteren.jazygit.ui.text.ShownTime;
import org.junit.jupiter.api.Test;

/**
 * The files listed before staying listed a moment while the next are read (ADR 141), and the frame drawn once they
 * stop being listed, which then says the next are read.
 */
class FilesPanelKeptWhileReadTest {

    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");
    private static final CommitFilesView FIRST = CommitFilesView.of(
                    new Changes.OfCommit(new Sha("aaaa")), Scopes.COMMITS.pane(), FilesPanel.PANE)
            .withFollowed(true)
            .withFiles(new Loadable.Loaded<>(List.of(new CommitFile("a.txt", ChangeType.MODIFIED))));

    private static Model listing(CommitFilesView view) {
        return TestModels.loaded()
                .withNow(NOW)
                .mapRepoView(repoView -> repoView.mapFiles(files -> files.withCommitFiles(Option.of(view))));
    }

    @Test
    void aFrameIsDrawnWhenTheFilesBeforeStopBeingListed() {
        CommitFilesView next = CommitFilesView.of(
                        new Changes.OfCommit(new Sha("bbbb")), Scopes.COMMITS.pane(), FilesPanel.PANE)
                .withFollowed(true)
                .after(Option.of(FIRST), NOW);

        assertEquals(
                Option.of(NOW.plus(CommitFilesView.KEPT_WHILE_READ)),
                ShownTime.next(FilesPanel.shownTimes(listing(next)), NOW));
    }

    @Test
    void theFilesBeforeAreKeptWhileTheListIsInUseButNotInThePane() throws Exception {
        Model model = listing(
                CommitFilesView.of(new Changes.OfCommit(new Sha("bbbb")), Scopes.COMMITS.pane(), FilesPanel.PANE)
                        .withFollowed(true)
                        .after(Option.of(FIRST), NOW));
        FilesPanel panel = new FilesPanel(msg -> {}, () -> {});
        Option<PaneId> commits = Option.of(Scopes.COMMITS.pane());
        try (ToolkitTestRunner testRunner =
                ToolkitTestRunner.runTest(() -> panel.render(model, Option.none(), commits))) {
            String fromCommits = RenderedText.ofLarge(testRunner, () -> panel.render(model, Option.none(), commits));
            assertTrue(fromCommits.contains("Files of aaaa"), fromCommits);
            assertTrue(fromCommits.contains("a.txt"), fromCommits);

            String inThePane = RenderedText.ofLarge(testRunner, () -> panel.render(model, FilesPanel.PANE));
            assertTrue(inThePane.contains("Files of bbbb"), inThePane);
            assertFalse(inThePane.contains("a.txt"), inThePane);
        }
    }

    @Test
    void manyFilesAreCountedAndEnterListsThem() throws Exception {
        List<CommitFile> many = List.range(0, CommitFilesView.FOLLOWED_LIMIT + 1)
                .map(i -> new CommitFile("f" + i + ".txt", ChangeType.MODIFIED));
        Model model = listing(FIRST.withFiles(new Loadable.Loaded<>(many)));
        java.util.List<Msg> sent = new java.util.ArrayList<>();
        FilesPanel panel = new FilesPanel(sent::add, () -> {});
        try (ToolkitTestRunner testRunner = ToolkitTestRunner.runTest(() -> panel.render(model, FilesPanel.PANE))) {
            String screen = RenderedText.ofLarge(testRunner, () -> panel.render(model, FilesPanel.PANE));
            assertTrue(screen.contains(" files changed · Enter to list them"), screen);
            assertFalse(screen.contains("f0.txt"), screen);

            SettledPilot.focus(testRunner, FilesPanel.ID);
            SettledPilot.of(testRunner).press(KeyCode.ENTER);
            SettledPilot.of(testRunner).quit();
        }

        assertEquals(java.util.List.of(new SelectionMsg.WholeListed()), sent);
    }

    @Test
    void noFrameIsAskedForOnceTheFilesAreRead() {
        CommitFilesView read = CommitFilesView.of(
                        new Changes.OfCommit(new Sha("bbbb")), Scopes.COMMITS.pane(), FilesPanel.PANE)
                .withFollowed(true)
                .after(Option.of(FIRST), NOW)
                .withFiles(new Loadable.Loaded<>(List.empty()));

        assertEquals(Option.none(), ShownTime.next(FilesPanel.shownTimes(listing(read)), NOW));
    }
}
