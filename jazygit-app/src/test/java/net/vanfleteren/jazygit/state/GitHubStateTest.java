package net.vanfleteren.jazygit.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.git.model.Branch;
import org.junit.jupiter.api.Test;

class GitHubStateTest {

    private static final Branch.Name FEATURE = new Branch.Name("feature");
    private static final PullRequest FEATURE_PR =
            new PullRequest(42, "feature", false, Checks.PASSING, Review.APPROVED);
    private static final GitHubState LISTED =
            GitHubState.NOT_READ.withPullRequests(Option.of(HashMap.of(FEATURE, FEATURE_PR)));

    private static PullRequestDetails details(int number, String headBranch, State state) {
        return new PullRequestDetails(
                number,
                "A pull request",
                "someone",
                state,
                true,
                headBranch,
                "main",
                Instant.EPOCH,
                0,
                0,
                0,
                PullRequestDetails.Mergeable.UNKNOWN,
                Review.CHANGES_REQUESTED,
                0,
                PullRequestDetails.ReviewThreads.NONE,
                List.empty(),
                List.empty(),
                List.of(new PullRequestDetails.Check("test", Checks.FAILING)));
    }

    @Test
    void aListedPullRequestThatWasReadIsListedAsItNowIs() {
        assertEquals(
                Option.of(HashMap.of(
                        FEATURE, new PullRequest(42, "feature", true, Checks.FAILING, Review.CHANGES_REQUESTED))),
                LISTED.withRead(details(42, "feature", State.OPEN)).pullRequests());
    }

    @Test
    void aListedPullRequestThatWasReadMergedOrClosedIsNoLongerListed() {
        Option<Map<Branch.Name, PullRequest>> none = Option.of(HashMap.empty());

        assertEquals(none, LISTED.withRead(details(42, "feature", State.MERGED)).pullRequests());
        assertEquals(none, LISTED.withRead(details(42, "feature", State.CLOSED)).pullRequests());
    }

    @Test
    void aPullRequestThatIsNotListedIsNotAdded() {
        // E.g. one from a fork, which the list leaves out, from a branch with the name of one here.
        assertEquals(LISTED, LISTED.withRead(details(7, "main", State.OPEN)));
        assertEquals(LISTED, LISTED.withRead(details(7, "feature", State.OPEN)));
    }

    @Test
    void beforeThePullRequestsAreReadNoneAreListed() {
        assertEquals(GitHubState.NOT_READ, GitHubState.NOT_READ.withRead(details(42, "feature", State.OPEN)));
    }
}
