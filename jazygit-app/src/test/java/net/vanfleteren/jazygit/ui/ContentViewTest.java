package net.vanfleteren.jazygit.ui;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.feature.branch.commits.BranchCommitsMsg;
import net.vanfleteren.jazygit.git.model.*;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.*;
import org.junit.jupiter.api.Test;

/**
 * Tests on {@link ContentView}: what the content panel shows in a model, without drawing it.
 */
class ContentViewTest {

    private static ContentView.Shown dim(String title, String text) {
        return new ContentView.Shown.Message(Messages.get(title), Messages.get(text), ContentView.Tone.DIM);
    }

    @Test
    void withoutCommitsTheCommitsPaneSaysSoDimmed() {
        Model empty = TestModels.loaded().mapRepo(repo -> repo.withCommits(new Loadable.Loaded<>(List.empty())));

        assertEquals(dim("panel.commit.title", "content.noCommits"), ContentView.of(empty, CommitsPanel.ID));
    }

    @Test
    void aBranchWithoutCommitsSaysSoDimmed() {
        BranchRef feature = new BranchRef.Local(new BranchName("feature"));
        Model logRead = Update.update(
                        Update.update(TestModels.loaded(), new SelectionMsg.BranchSelected(feature))
                                .model(),
                        TestModels.branchLogLoaded(feature, List.empty()))
                .model();
        Model open =
                Update.update(logRead, new BranchCommitsMsg.Opened(feature)).model();

        assertEquals(dim("panel.commit.title", "content.noCommits"), ContentView.of(open, BranchesPanel.ID));
    }
}
