package net.vanfleteren.jazygit.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.GhBranch;
import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.github.checks.ChecksPopup;
import net.vanfleteren.jazygit.features.github.comments.CommentsPopup;
import net.vanfleteren.jazygit.features.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.features.github.state.Reading;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.TestModels;
import org.junit.jupiter.api.Test;

/**
 * The tests of SpinnersTest with GitHub in the app (ADR 171).
 */
class SpinnersGitHubTest {

    private static final Model LOADED = TestModels.loaded(SampleData.git());

    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");

    private static final BranchRef.Local FEATURE = new BranchRef.Local(new BranchName("feature"));

    private static final PullRequestPopup PULL_REQUEST = PullRequestPopup.loading(
            new BranchName("feature"),
            new PullRequest(new PrNumber(42), new GhBranch("feature"), true, Checks.PASSING, Review.APPROVED),
            Instant.EPOCH,
            0);

    @Test
    void thePullRequestDialogShowsOneWhileItsDetailsAreRead() {
        assertTrue(Spinners.shown(LOADED.openPopup(PULL_REQUEST), NOW));
        assertFalse(Spinners.shown(
                LOADED.openPopup(PULL_REQUEST.with(Either.left(TestModels.refused("offline")), 0)), NOW));
    }

    @Test
    void theCommentsDialogShowsOneWhileTheCommentsAreRead() {
        CommentsPopup loading = CommentsPopup.loading(PULL_REQUEST);

        assertTrue(Spinners.shown(LOADED.openPopup(loading), NOW));
        assertFalse(Spinners.shown(LOADED.openPopup(loading.read(Either.right(List.empty()))), NOW));
    }

    @Test
    void theChecksDialogShowsOneWhileItsChecksAreFirstReadAndReadAgainAsAsked() {
        ChecksPopup loading =
                ChecksPopup.loading(FEATURE, new RemoteBranch("origin", new BranchName("feature")), Instant.EPOCH);
        ChecksPopup read = loading.with(Either.left(TestModels.refused("offline")));

        assertTrue(Spinners.shown(LOADED.openPopup(loading), NOW));
        assertFalse(Spinners.shown(LOADED.openPopup(read), NOW));
        assertFalse(Spinners.shown(LOADED.openPopup(read.readingAgain(Reading.BY_ITSELF, NOW)), NOW));
        assertTrue(Spinners.shown(LOADED.openPopup(read.readingAgain(Reading.ASKED, NOW)), NOW));
    }
}
