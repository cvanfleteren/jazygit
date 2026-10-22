package net.vanfleteren.jazygit.gh.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import org.junit.jupiter.api.Test;

class PullRequestDetailsTest {

    private static PullRequestDetails details(State state, List<Check> checks) {
        return new PullRequestDetails(
                42,
                "A pull request",
                "someone",
                state,
                true,
                "feature",
                "main",
                Instant.EPOCH,
                Instant.EPOCH,
                0,
                0,
                0,
                PullRequestDetails.Mergeable.UNKNOWN,
                Review.REQUIRED,
                0,
                PullRequestDetails.ReviewThreads.NONE,
                List.empty(),
                List.empty(),
                checks);
    }

    private static Option<Checks> listedChecks(Check... checks) {
        return details(State.OPEN, List.of(checks)).listed().map(PullRequest::checks);
    }

    @Test
    void anOpenPullRequestIsListedAsItIs() {
        assertEquals(
                Option.of(new PullRequest(42, "feature", true, Checks.PASSING, Review.REQUIRED)),
                details(State.OPEN, List.of(new Check("build", Checks.PASSING))).listed());
    }

    @Test
    void aMergedOrClosedPullRequestIsNotListed() {
        assertEquals(Option.none(), details(State.MERGED, List.empty()).listed());
        assertEquals(Option.none(), details(State.CLOSED, List.empty()).listed());
    }

    @Test
    void itsChecksAreListedCombined() {
        assertEquals(Option.of(Checks.NONE), listedChecks());
        assertEquals(Option.of(Checks.PASSING), listedChecks(new Check("build", Checks.PASSING)));
        assertEquals(
                Option.of(Checks.PENDING),
                listedChecks(new Check("build", Checks.PASSING), new Check("test", Checks.PENDING)));
        assertEquals(
                Option.of(Checks.FAILING),
                listedChecks(new Check("build", Checks.PENDING), new Check("test", Checks.FAILING)));
    }
}
