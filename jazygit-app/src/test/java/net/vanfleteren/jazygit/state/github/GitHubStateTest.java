package net.vanfleteren.jazygit.state.github;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.MergeStatus;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Activity;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Branches;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Conversation;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Size;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Status;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.github.prs.LoadRepositoryPullRequests;
import org.junit.jupiter.api.Test;

class GitHubStateTest {

    private static final BranchName FEATURE = new BranchName("feature");
    private static final PullRequest FEATURE_PR =
            new PullRequest(42, new GhBranch("feature"), false, Checks.PASSING, Review.APPROVED);
    private static final GitHubState LISTED =
            GitHubState.NOT_READ.withPullRequests(Option.of(HashMap.of(FEATURE, FEATURE_PR)));

    private static PullRequestDetails details(int number, String headBranch, State state) {
        return new PullRequestDetails(
                number,
                "A pull request",
                "someone",
                new Status(state, true, PullRequestDetails.Mergeable.UNKNOWN),
                new Branches(new GhBranch(headBranch), new GhBranch("main")),
                new Activity(Instant.EPOCH, Instant.EPOCH),
                new Size(0, 0, 0, 1),
                new Conversation(
                        Review.CHANGES_REQUESTED, List.empty(), 0, PullRequestDetails.ReviewThreads.NONE, List.empty()),
                List.of(new PullRequestDetails.Check("test", Checks.FAILING)));
    }

    @Test
    void aListedPullRequestThatWasReadIsListedAsItNowIs() {
        assertEquals(
                Option.of(HashMap.of(
                        FEATURE,
                        new PullRequest(
                                42,
                                new GhBranch("feature"),
                                Option.of(new GhBranch("main")),
                                true,
                                Checks.FAILING,
                                Review.CHANGES_REQUESTED,
                                Option.none(),
                                Option.none()))),
                LISTED.withRead(details(42, "feature", State.OPEN), 0).pullRequests());
    }

    @Test
    void aListedPullRequestThatWasReadMergedOrClosedIsNoLongerListed() {
        Option<Map<BranchName, PullRequest>> none = Option.of(HashMap.empty());

        assertEquals(
                none, LISTED.withRead(details(42, "feature", State.MERGED), 0).pullRequests());
        assertEquals(
                none, LISTED.withRead(details(42, "feature", State.CLOSED), 0).pullRequests());
    }

    @Test
    void aPullRequestThatIsNotListedIsNotAdded() {
        // E.g. one from a fork, which the list leaves out, from a branch with the name of one here.
        assertEquals(LISTED, LISTED.withRead(details(7, "main", State.OPEN), 0));
        assertEquals(LISTED, LISTED.withRead(details(7, "feature", State.OPEN), 0));
    }

    @Test
    void beforeThePullRequestsAreReadNoneAreListed() {
        assertEquals(GitHubState.NOT_READ, GitHubState.NOT_READ.withRead(details(42, "feature", State.OPEN), 0));
    }

    @Test
    void aReadOfTheOpenPullRequestsGetsTheIdOfTheNextRead() {
        GitHubState read = GitHubState.NOT_READ.withReadGivenOut();

        GitHubState started = read.withStarted(List.of(new LoadRepositoryPullRequests()));

        assertEquals(1, started.listRead());
        assertEquals(2, started.nextRead());
        assertEquals(read, read.withStarted(List.of(new LoadProtectedBranches())));
    }

    private static final PullRequest PENDING_PR =
            new PullRequest(42, new GhBranch("feature"), false, Checks.PENDING, Review.APPROVED);
    private static final MergeStatus.Failed CONFLICTS = new MergeStatus.Failed("conflicts");

    private static GitHubState failed(GitHubState state) {
        return state.withMerge(PullRequestMerge.asked(42, FEATURE, List.empty(), CONFLICTS, Instant.EPOCH));
    }

    @Test
    void aListReadThatStartedBeforeTheMergeFailedDoesNotDropTheFailure() {
        // The list is read while the checks run; the dialog then reads them passing, and the merge fails anyway.
        GitHubState reading = GitHubState.NOT_READ
                .withListed(List.of(PENDING_PR))
                .withStarted(List.of(new LoadRepositoryPullRequests()))
                .withReadGivenOut()
                .withPullRequests(Option.of(HashMap.of(FEATURE, FEATURE_PR)));
        GitHubState failed = failed(reading);

        GitHubState stale = failed.withListed(List.of(PENDING_PR));
        GitHubState again = stale.withStarted(List.of(new LoadRepositoryPullRequests()));

        assertEquals(Option.of(CONFLICTS), stale.merge(42));
        assertEquals(Option.of(CONFLICTS), again.withListed(List.of(FEATURE_PR)).merge(42));
        assertEquals(Option.none(), again.withListed(List.of(PENDING_PR)).merge(42));
    }

    @Test
    void aDetailsReadGivenOutAfterTheMergeFailedDropsTheFailureOnceThePullRequestChanged() {
        GitHubState before = LISTED.withReadGivenOut();
        GitHubState failed = failed(before);
        long after = failed.nextRead();
        PullRequestDetails changed = details(42, "feature", State.OPEN);

        assertEquals(Option.of(CONFLICTS), failed.withRead(changed, after - 1).merge(42));
        assertEquals(Option.none(), failed.withRead(changed, after).merge(42));
    }
}
