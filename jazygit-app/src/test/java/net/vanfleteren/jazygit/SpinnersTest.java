package net.vanfleteren.jazygit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.feature.branch.push.PushCmd;
import net.vanfleteren.jazygit.feature.branch.tabs.BranchesTabMsg;
import net.vanfleteren.jazygit.feature.github.comments.CommentsPopup;
import net.vanfleteren.jazygit.feature.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.BranchesTab;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import org.junit.jupiter.api.Test;

/**
 * Frames are drawn for the spinners exactly while one shows.
 */
class SpinnersTest {

    private static final Model LOADED = TestModels.loaded(new SampleData());
    private static final BranchRef.Local FEATURE = new BranchRef.Local(new BranchName("feature"));
    private static final PullRequestPopup PULL_REQUEST = PullRequestPopup.loading(
            new BranchName("feature"), new PullRequest(42, "feature", true, Checks.PASSING, Review.APPROVED));

    @Test
    void noneShowsWhenNothingRuns() {
        assertFalse(Spinners.shown(LOADED));
    }

    @Test
    void aBusyBranchShowsOneInTheListThatHasIt() {
        Model busy = LOADED.withBusyBranch(FEATURE, PushCmd.PUSH);

        assertTrue(Spinners.shown(busy));
        assertFalse(Spinners.shown(Update.update(busy, new BranchesTabMsg.Chosen(BranchesTab.REMOTE))
                .model()));
    }

    @Test
    void thePullRequestDialogShowsOneWhileItsDetailsAreRead() {
        assertTrue(Spinners.shown(LOADED.openPopup(PULL_REQUEST)));
        assertFalse(Spinners.shown(LOADED.openPopup(PULL_REQUEST.with(Either.left(TestModels.refused("offline"))))));
    }

    @Test
    void theCommentsDialogShowsOneWhileTheCommentsAreRead() {
        CommentsPopup loading = CommentsPopup.loading(PULL_REQUEST);

        assertTrue(Spinners.shown(LOADED.openPopup(loading)));
        assertFalse(Spinners.shown(LOADED.openPopup(loading.read(Either.right(List.empty())))));
    }
}
