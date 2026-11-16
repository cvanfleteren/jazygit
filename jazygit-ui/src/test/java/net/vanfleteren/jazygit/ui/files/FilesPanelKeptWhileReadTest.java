package net.vanfleteren.jazygit.ui.files;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Loadable;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.diff.Changes;
import net.vanfleteren.jazygit.state.view.CommitFilesView;
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
    void noFrameIsAskedForOnceTheFilesAreRead() {
        CommitFilesView read = CommitFilesView.of(
                        new Changes.OfCommit(new Sha("bbbb")), Scopes.COMMITS.pane(), FilesPanel.PANE)
                .withFollowed(true)
                .after(Option.of(FIRST), NOW)
                .withFiles(new Loadable.Loaded<>(List.empty()));

        assertEquals(Option.none(), ShownTime.next(FilesPanel.shownTimes(listing(read)), NOW));
    }
}
