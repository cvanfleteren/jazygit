package net.vanfleteren.jazygit.state.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.MergeStatus;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.git.model.BranchName;
import org.junit.jupiter.api.Test;

class PullRequestMergeTest {

    private static final long FAILED_AT = 5;

    private static PullRequest at(String head, boolean draft) {
        return new PullRequest(
                new PrNumber(42),
                new GhBranch("feature"),
                draft,
                Checks.PENDING,
                Review.APPROVED,
                Option.none(),
                Option.of(new PullRequest.Commits(
                        "owner/repo", "https://github.com/owner/repo", new GhCommit("base"), new GhCommit(head))));
    }

    private static final PullRequest FEATURE_PR = at("abc", false);

    private static PullRequestMerge merge(MergeStatus status) {
        return PullRequestMerge.asked(new PrNumber(42), new BranchName("feature"), List.empty(), status, Instant.EPOCH);
    }

    private static PullRequestMerge failedWhile(List<PullRequest> open) {
        return merge(new MergeStatus.Failed("conflicts")).failedAmong(open, FAILED_AT);
    }

    @Test
    void aReadThatShowsThePullRequestStandingTheSameKeepsTheFailure() {
        assertFalse(failedWhile(List.of(FEATURE_PR)).outdatedBy(List.of(FEATURE_PR), FAILED_AT));
    }

    @Test
    void aPushToThePullRequestOutdatesTheFailure() {
        assertTrue(failedWhile(List.of(FEATURE_PR)).outdatedBy(List.of(at("def", false)), FAILED_AT));
    }

    @Test
    void thePullRequestTurnedIntoADraftOutdatesTheFailure() {
        assertTrue(failedWhile(List.of(FEATURE_PR)).outdatedBy(List.of(at("abc", true)), FAILED_AT));
    }

    @Test
    void thePullRequestNoLongerListedOutdatesTheFailure() {
        assertTrue(failedWhile(List.of(FEATURE_PR)).outdatedBy(List.empty(), FAILED_AT));
    }

    @Test
    void aReadThatStartedBeforeTheFailureDoesNotOutdateIt() {
        assertFalse(failedWhile(List.of(FEATURE_PR)).outdatedBy(List.of(at("def", false)), FAILED_AT - 1));
    }

    @Test
    void aFailureWhileThePullRequestWasNotListedStaysWhateverIsRead() {
        PullRequestMerge failed = failedWhile(List.empty());

        assertFalse(failed.outdatedBy(List.of(FEATURE_PR), FAILED_AT));
        assertFalse(failed.outdatedBy(List.empty(), FAILED_AT));
    }

    @Test
    void howThePullRequestsStoodIsKeptOnlyOnceItFailed() {
        PullRequestMerge pending = merge(new MergeStatus.Pending("merge-42"));
        PullRequestMerge failed = failedWhile(List.of(FEATURE_PR));

        assertEquals(pending, pending.failedAmong(List.of(FEATURE_PR), FAILED_AT));
        assertFalse(pending.outdatedBy(List.empty(), FAILED_AT));
        assertEquals(failed, failed.failedAmong(List.of(at("def", false)), FAILED_AT + 1));
    }

    @Test
    void aPullRequestThatIsNotMergedByItIsNotLookedAt() {
        PullRequest other =
                new PullRequest(new PrNumber(7), new GhBranch("other"), false, Checks.PENDING, Review.APPROVED);
        PullRequestMerge failed = failedWhile(List.of(FEATURE_PR, other));

        assertFalse(failed.outdatedBy(List.of(FEATURE_PR), FAILED_AT));
    }
}
