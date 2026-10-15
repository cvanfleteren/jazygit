package net.vanfleteren.jazygit.gh;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.BranchChecks;
import net.vanfleteren.jazygit.gh.model.GhLogin;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Mergeable;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Reviewer;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.ReviewerState;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void parsesTheRepositoryAndTheChecksOfEachBranchOverAllPages() {
        String raw = "repo\towner/repo\n"
                + "branch\tfeature/login\tSUCCESS\n"
                + "branch\twip\t\n"
                + "repo\towner/repo\n"
                + "branch\tmain\tFAILURE\n"
                + "branch\tfix\tEXPECTED\n";

        assertEquals(Either.right(new BranchChecks("owner/repo", HashMap.of(
                        "feature/login", Checks.PASSING, "wip", Checks.NONE, "main", Checks.FAILING, "fix", Checks.PENDING))),
                CliGitHub.parseBranchChecks(raw));
    }

    @Test
    void branchChecksWithoutTheRepositoryAreUnexpected() {
        assertTrue(CliGitHub.parseBranchChecks("branch\tmain\tSUCCESS\n").isLeft());
        assertTrue(CliGitHub.parseBranchChecks("repo\towner/repo\nsomething\telse\n").isLeft());
    }

    @Test
    void aRemotePointsAtTheRepositoryOverSshOrHttps() {
        BranchChecks checks = new BranchChecks("Owner/repo", HashMap.empty());

        assertTrue(checks.isAt("git@github.com:owner/repo.git"));
        assertTrue(checks.isAt("https://github.com/Owner/repo"));
        assertTrue(checks.isAt("https://github.com/owner/repo.git/"));
        assertTrue(checks.isAt("ssh://git@github.com/owner/repo.git"));
        assertTrue(checks.isAt("github-work:owner/repo.git"));
        assertFalse(checks.isAt("git@github.com:fork/repo.git"));
        assertFalse(checks.isAt("git@github.com:owner/repository.git"));
        assertFalse(checks.isAt("git@github.com:other-owner/repo.git"));
    }

    @Test
    void browsingOpensTheBranchByItsNameOnTheRemote() {
        assertEquals(List.of("gh", "browse", "--branch", "feature/login"),
                CliGitHub.browseCommand("feature/login").commandLine("gh"));
    }

    @Test
    void theVersionIsInTheFirstLine() {
        assertEquals(Either.right("2.102.0"), CliGitHub.parseVersion("""
                gh version 2.102.0 (2026-09-30)
                https://github.com/cli/cli/releases/tag/v2.102.0
                """));
        assertTrue(CliGitHub.parseVersion("Usage: something else").isLeft());
    }

    @Test
    void theLoginsOnEachHost() {
        assertEquals(Either.right(List.of(new GhLogin("github.com", "alice", true, true),
                        new GhLogin("github.com", "alice-work", false, false),
                        new GhLogin("ghe.corp", "alice", true, true))),
                CliGitHub.parseLogins("""
                        github.com\talice\ttrue\tsuccess
                        github.com\talice-work\tfalse\terror
                        ghe.corp\talice\ttrue\tsuccess
                        """));
    }

    @Test
    void ghRunsFromItsExecutableButIsLoggedAsGh() {
        GhCommand command = CliGitHub.browseCommand("main");

        assertEquals("/opt/gh/bin/gh", command.commandLine("/opt/gh/bin/gh").head());
        assertEquals(List.of("gh", "browse", "--branch", "main"), command.executed().commandLine());
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
    void theNumberOfACreatedPullRequestIsInTheUrlGhPrints() {
        assertEquals(Option.of(12), CliGitHub.createdNumber("\nCreating pull request...\nhttps://github.com/o/r/pull/12\n"));
        assertEquals(Option.none(), CliGitHub.createdNumber("nothing useful"));
    }

    @Test
    void readsTheReviewDecision() {
        assertEquals(Review.REQUIRED, CliGitHub.review("REVIEW_REQUIRED"));
        assertEquals(Review.APPROVED, CliGitHub.review("APPROVED"));
        assertEquals(Review.CHANGES_REQUESTED, CliGitHub.review("CHANGES_REQUESTED"));
        assertEquals(Review.NONE, CliGitHub.review(""));
    }

    @Test
    void parsesTheDetailsOfAPullRequest() {
        String raw = "pr\t42\tAdd\\trename\tcvf\tOPEN\ttrue\tsupport_rename\tmain\t2026-10-10T08:00:00Z\t120\t34\t7"
                + "\tMERGEABLE\tCHANGES_REQUESTED\t2\n"
                + "label\tui\n"
                + "review\tbob\tCHANGES_REQUESTED\n"
                + "review\tcarol\tAPPROVED\n"
                + "review\tdave\tDISMISSED\n"
                + "requested\tcarol\n"
                + "requested\tcore-team\n"
                + "check\tbuild\tPENDING\n"
                + "check\tci/lint\tSUCCESS\n";

        assertEquals(Either.right(new PullRequestDetails(42, "Add\trename", "cvf", State.OPEN, true, "support_rename",
                        "main", Instant.parse("2026-10-10T08:00:00Z"), 120, 34, 7, Mergeable.MERGEABLE,
                        Review.CHANGES_REQUESTED, 2, List.of("ui"),
                        List.of(new Reviewer("bob", ReviewerState.CHANGES_REQUESTED),
                                new Reviewer("carol", ReviewerState.REQUESTED),
                                new Reviewer("core-team", ReviewerState.REQUESTED)),
                        List.of(new Check("build", Checks.PENDING), new Check("ci/lint", Checks.PASSING)))),
                CliGitHub.parseDetails(raw));
    }

    @Test
    void aMergedPullRequestWithoutChecksLabelsOrReviewers() {
        String raw = "pr\t5\tFix\t\tMERGED\tfalse\tfix\tmain\t2026-01-01T00:00:00Z\t1\t0\t1\tUNKNOWN\t\t0\n";

        assertEquals(Either.right(new PullRequestDetails(5, "Fix", "", State.MERGED, false, "fix", "main",
                        Instant.parse("2026-01-01T00:00:00Z"), 1, 0, 1, Mergeable.UNKNOWN, Review.NONE, 0,
                        List.empty(), List.empty(), List.empty())),
                CliGitHub.parseDetails(raw));
    }

    @Test
    void detailsThatCannotBeReadAreUnexpectedOutput() {
        assertTrue(CliGitHub.parseDetails("").isLeft());
        assertTrue(CliGitHub.parseDetails("label\tui\n").isLeft());
        assertTrue(CliGitHub.parseDetails("pr\tx\n").isLeft());
        assertTrue(CliGitHub.parseDetails("something\telse\n").isLeft());
    }
}
