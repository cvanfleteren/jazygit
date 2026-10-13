package net.vanfleteren.jazygit.gh;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliGitHubTest {

    @Test
    void parsesOneTabSeparatedLinePerPullRequest() {
        String raw = "9\tfeature/login\tfalse\tAPPROVED\tSUCCESS,SKIPPED\n"
                + "7\twip\ttrue\t\t\n"
                + "5\tfix/npe\tfalse\tCHANGES_REQUESTED\tSUCCESS,PENDING\n";

        assertEquals(Either.right(List.of(
                        new PullRequest(9, "feature/login", false, Checks.PASSING, Review.APPROVED),
                        new PullRequest(7, "wip", true, Checks.NONE, Review.NONE),
                        new PullRequest(5, "fix/npe", false, Checks.PENDING, Review.CHANGES_REQUESTED))),
                CliGitHub.parse(raw));
    }

    @Test
    void noPullRequestsIsAnEmptyList() {
        assertEquals(Either.right(List.empty()), CliGitHub.parse(""));
    }

    @Test
    void aLineThatCannotBeReadIsUnexpectedOutput() {
        assertTrue(CliGitHub.parse("not a pull request").isLeft());
        assertTrue(CliGitHub.parse("x\tbranch\tfalse\t\t").isLeft());
    }

    @Test
    void oneFailedCheckFailsThemAllElseOneRunningKeepsThemPending() {
        assertEquals(Checks.NONE, CliGitHub.checks(List.empty()));
        assertEquals(Checks.PASSING, CliGitHub.checks(List.of("SUCCESS", "NEUTRAL", "SKIPPED")));
        assertEquals(Checks.PENDING, CliGitHub.checks(List.of("SUCCESS", "PENDING")));
        assertEquals(Checks.FAILING, CliGitHub.checks(List.of("PENDING", "FAILURE")));
        assertEquals(Checks.FAILING, CliGitHub.checks(List.of("SUCCESS", "ERROR")));
    }

    @Test
    void readsTheReviewDecision() {
        assertEquals(Review.REQUIRED, CliGitHub.review("REVIEW_REQUIRED"));
        assertEquals(Review.APPROVED, CliGitHub.review("APPROVED"));
        assertEquals(Review.CHANGES_REQUESTED, CliGitHub.review("CHANGES_REQUESTED"));
        assertEquals(Review.NONE, CliGitHub.review(""));
    }
}
