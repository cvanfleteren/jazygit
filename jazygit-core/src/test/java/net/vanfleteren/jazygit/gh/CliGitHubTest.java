package net.vanfleteren.jazygit.gh;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.BranchChecks;
import net.vanfleteren.jazygit.gh.model.GhLogin;
import net.vanfleteren.jazygit.gh.model.MergeMethod;
import net.vanfleteren.jazygit.gh.model.MergeStatus;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestComment;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.*;
import net.vanfleteren.jazygit.gh.model.PullRequestList;
import net.vanfleteren.jazygit.gh.model.PullRequestPreview;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliGitHubTest {

    /*
     * The details of a pull request in state, at commit head, into main, as read through a gh that runs compare when
     * asked how far behind its base it is, writing what it was asked with to the file "compared" in dir.
     */
    private static Either<GhError, PullRequestDetails> detailsFrom(Path dir, String state, String head, String compare)
            throws Exception {
        Path gh = Files.writeString(
                dir.resolve("gh"),
                "#!/bin/sh\n"
                        + "case \"$*\" in\n"
                        + "  *base=refs/heads/*) echo \" $* \" > '" + dir.resolve("compared") + "'; " + compare
                        + " ;;\n"
                        + "  *) printf 'pr\\t95\\tFix\\t\\t" + state
                        + "\\tfalse\\tfix\\tmain\\t2026-01-01T00:00:00Z\\t2026-01-01T00:00:00Z\\t1\\t0\\t1\\tMERGEABLE"
                        + "\\t\\t0\\t" + head + "\\nthreads\\t0\\t0\\t0\\t0\\n' ;;\n"
                        + "esac\n");
        gh.toFile().setExecutable(true);
        return new CliGitHub(dir, gh.toString(), GhToken.NONE).pullRequestDetails(95);
    }

    @Test
    void parsesOneTabSeparatedLinePerPullRequest() {
        String raw = "9\tfeature/login\tfalse\tAPPROVED\tSUCCESS,SKIPPED\t\t\t\n"
                + "7\twip\ttrue\t\t\t\t\t\n"
                + "5\tfix/npe\tfalse\tCHANGES_REQUESTED\tSUCCESS,PENDING\t\t\t\n";

        assertEquals(
                Either.right(List.of(
                        new PullRequest(9, "feature/login", false, Checks.PASSING, Review.APPROVED),
                        new PullRequest(7, "wip", true, Checks.NONE, Review.NONE),
                        new PullRequest(5, "fix/npe", false, Checks.PENDING, Review.CHANGES_REQUESTED))),
                CliGitHub.parse(raw));
    }

    @Test
    void aStackedPullRequestSaysWhereItSitsInItsStack() {
        assertEquals(
                Either.right(List.of(new PullRequest(
                        95,
                        "fix-abort-amend",
                        false,
                        Checks.NONE,
                        Review.NONE,
                        Option.of(new PullRequestStack(96, 2, 2))))),
                CliGitHub.parse("95\tfix-abort-amend\tfalse\t\t\t96\t2\t2\n"));
        assertTrue(
                CliGitHub.parse("95\tfix-abort-amend\tfalse\t\t\t96\ttwo\t2\n").isLeft());
    }

    @Test
    void aStackedPullRequestInAListSaysWhereItSitsInItsStack() {
        String raw = "list\trepository\towner/repo\t1\n"
                + "pr\trepository\towner/repo\t95\tFix\talice\tfix\tfalse\tfalse\t\t\t2026-10-15T08:00:00Z"
                + "\thttps://github.com/owner/repo/pull/95\t96\t2\t2\n";

        assertEquals(
                Option.of(new PullRequestStack(96, 2, 2)),
                CliGitHub.parseLists(raw)
                        .get()
                        .get("repository")
                        .get()
                        ._2
                        .pullRequests()
                        .head()
                        .stack());
    }

    @Test
    void theDetailsOfAStackedPullRequestSayWhereItSitsAndWhatItsHeadIs() {
        String raw =
                "pr\t95\tFix\t\tOPEN\tfalse\tfix\tmain\t2026-01-01T00:00:00Z\t2026-01-01T00:00:00Z\t1\t0\t1\tUNKNOWN\t\t0"
                        + "\tabc123\n"
                        + "threads\t0\t0\t0\t0\n"
                        + "stack\t96\t2\t2\n";

        PullRequestDetails details = CliGitHub.parseDetails(raw).get();

        assertEquals("abc123", details.head());
        assertEquals(Option.of(new PullRequestStack(96, 2, 2)), details.stack());
        assertEquals(
                Option.of(new PullRequestStack(96, 2, 2)),
                details.listed().get().stack());
        assertTrue(CliGitHub.parseDetails(raw + "stack\t96\t2\n").isLeft());
    }

    @Test
    void parsesHowFarBehindItsBaseAPullRequestIs() {
        assertEquals(Either.right(Option.of(3)), CliGitHub.parseBehind("3\n"));
        assertEquals(Either.right(Option.none()), CliGitHub.parseBehind("\n"));
        assertTrue(CliGitHub.parseBehind("many\n").isLeft());
        assertEquals(
                Option.none(),
                CliGitHub.parseDetails(
                                "pr\t95\tFix\t\tOPEN\tfalse\tfix\tmain\t2026-01-01T00:00:00Z\t2026-01-01T00:00:00Z\t1\t0\t1"
                                        + "\tMERGEABLE\t\t0\tabc123\nthreads\t0\t0\t0\t0\n")
                        .get()
                        .behindBase());
    }

    @Test
    void aMergeIsAskedOfTheRestApiWithItsMethodAndOnlyAtTheHeadThatWasRead() {
        assertEquals(
                List.of(
                        "gh",
                        "api",
                        "--method",
                        "PUT",
                        "repos/{owner}/{repo}/pulls/95/merge-async",
                        "-f",
                        "merge_method=squash",
                        "-f",
                        "sha=abc123"),
                CliGitHub.mergeCommand(95, "abc123", MergeMethod.SQUASH)
                        .executed()
                        .commandLine()
                        .dropRight(2));
        assertFalse(CliGitHub.mergeCommand(95, "", MergeMethod.REBASE)
                .executed()
                .commandLine()
                .exists(arg -> arg.startsWith("sha=")));
        assertEquals(
                List.of("gh", "api", "repos/{owner}/{repo}/pulls/95/merge-async/u-1"),
                CliGitHub.mergeStatusCommand(95, "u-1").executed().commandLine().dropRight(2));
    }

    @Test
    void parsesHowAMergeIsDoing() {
        assertEquals(Either.right(new MergeStatus.Pending("u-1")), CliGitHub.parseMergeStatus("pending\tu-1\t\n"));
        assertEquals(Either.right(new MergeStatus.Merged()), CliGitHub.parseMergeStatus("merged\t\tMerged\n"));
        assertEquals(Either.right(new MergeStatus.Enqueued()), CliGitHub.parseMergeStatus("enqueued\t\t\n"));
        assertEquals(
                Either.right(new MergeStatus.Failed("Required status check \"build\" is failing")),
                CliGitHub.parseMergeStatus("failed\t\tRequired status check \"build\" is failing\n"));
        assertTrue(CliGitHub.parseMergeStatus("pending\t\t\n").isLeft());
        assertTrue(CliGitHub.parseMergeStatus("done\t\t\n").isLeft());
        assertTrue(CliGitHub.parseMergeStatus("").isLeft());
    }

    @Test
    void parsesTheListsOfPullRequestsWithTheirRepositoryAndHowManyMatch() {
        String raw = "list\treviewRequested\t\t52\n"
                + "pr\treviewRequested\tother/repo\t9\tFix the\\tlogin\talice\tfeature/login\tfalse\tfalse"
                + "\tAPPROVED\tSUCCESS\t2026-10-16T21:39:59Z\thttps://github.com/other/repo/pull/9\t\t\t\n"
                + "list\tauthored\t\t0\n"
                + "list\trepository\towner/repo\t1\n"
                + "pr\trepository\towner/repo\t7\tWIP\t\tpatch-1\ttrue\ttrue\t\t\t2026-10-15T08:00:00Z"
                + "\thttps://github.com/owner/repo/pull/7\t\t\t\n";

        var lists = CliGitHub.parseLists(raw).get();

        assertEquals(
                Tuple.of(
                        "",
                        new PullRequestList(
                                List.of(new PullRequestSummary(
                                        "other/repo",
                                        9,
                                        "Fix the\tlogin",
                                        "alice",
                                        "feature/login",
                                        false,
                                        false,
                                        Checks.PASSING,
                                        Review.APPROVED,
                                        Instant.parse("2026-10-16T21:39:59Z"),
                                        "https://github.com/other/repo/pull/9")),
                                52)),
                lists.get("reviewRequested").get());
        assertEquals(Tuple.of("", PullRequestList.EMPTY), lists.get("authored").get());
        assertEquals(
                Tuple.of(
                        "owner/repo",
                        new PullRequestList(
                                List.of(new PullRequestSummary(
                                        "owner/repo",
                                        7,
                                        "WIP",
                                        "",
                                        "patch-1",
                                        true,
                                        true,
                                        Checks.NONE,
                                        Review.NONE,
                                        Instant.parse("2026-10-15T08:00:00Z"),
                                        "https://github.com/owner/repo/pull/7")),
                                1)),
                lists.get("repository").get());
    }

    @Test
    void thePreviewHasTheBaseOnTheFirstLineAndTheBodyAfterIt() {
        assertEquals(
                new PullRequestPreview("main", "Fixes the login.\n\nSee #3.", "+a\n", false),
                CliGitHub.preview("main\nFixes the login.\n\nSee #3.\n", "+a\n"));
        assertEquals(new PullRequestPreview("main", "", "", false), CliGitHub.preview("main\n", ""));
    }

    @Test
    void aLongDiffIsCutOff() {
        String diff = "+line\n".repeat(CliGitHub.MAX_DIFF_LINES + 10);

        PullRequestPreview preview = CliGitHub.preview("main\n", diff);

        assertTrue(preview.truncated());
        assertEquals(CliGitHub.MAX_DIFF_LINES, preview.diff().lines().count());
    }

    @Test
    void aPullRequestLineThatCannotBeReadIsUnexpected() {
        assertTrue(CliGitHub.parseLists("list\tauthored\t\t1\npr\tauthored\towner/repo\tnine\n")
                .isLeft());
    }

    @Test
    void aListWhoseCountIsNoNumberIsUnexpected() {
        assertTrue(CliGitHub.parseLists("list\tauthored\t\tmany\n").isLeft());
        assertTrue(CliGitHub.parseLists("list\tauthored\t\n").isLeft());
    }

    @Test
    void parsesTheRepositoryAndTheChecksOfEachBranchOverAllPages() {
        String raw = "repo\towner/repo\n"
                + "branch\tfeature/login\tSUCCESS\n"
                + "branch\twip\t\n"
                + "repo\towner/repo\n"
                + "branch\tmain\tFAILURE\n"
                + "branch\tfix\tEXPECTED\n";

        assertEquals(
                Either.right(new BranchChecks(
                        "owner/repo",
                        HashMap.of(
                                "feature/login",
                                Checks.PASSING,
                                "wip",
                                Checks.NONE,
                                "main",
                                Checks.FAILING,
                                "fix",
                                Checks.PENDING))),
                CliGitHub.parseBranchChecks(raw));
    }

    @Test
    void branchChecksWithoutTheRepositoryAreUnexpected() {
        assertTrue(CliGitHub.parseBranchChecks("branch\tmain\tSUCCESS\n").isLeft());
        assertTrue(CliGitHub.parseBranchChecks("repo\towner/repo\nsomething\telse\n")
                .isLeft());
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
        assertEquals(
                List.of("gh", "browse", "--branch", "feature/login"),
                CliGitHub.browseCommand("feature/login").commandLine("gh"));
    }

    @Test
    void aPullRequestIsCreatedWithItsTitleAndBodyAndAsADraftWhenAsked() {
        assertEquals(
                List.of(
                        "gh",
                        "pr",
                        "create",
                        "--head",
                        "feature",
                        "--base",
                        "main",
                        "--title",
                        "Add login",
                        "--body",
                        ""),
                CliGitHub.createCommand("feature", "main", new NewPullRequest("Add login", "", false))
                        .executed()
                        .commandLine());
        assertEquals(
                List.of(
                        "gh",
                        "pr",
                        "create",
                        "--head",
                        "feature",
                        "--base",
                        "main",
                        "--title",
                        "Add login",
                        "--body",
                        "Why",
                        "--draft"),
                CliGitHub.createCommand("feature", "main", new NewPullRequest("Add login", "Why", true))
                        .executed()
                        .commandLine());
    }

    @Test
    void aCommentIsAddedWithItsBodyAsTyped() {
        assertEquals(
                List.of("gh", "pr", "comment", "12", "--body", "Looks good\n\n- one nit"),
                CliGitHub.commentCommand(12, "Looks good\n\n- one nit")
                        .executed()
                        .commandLine());
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
        assertEquals(
                Either.right(List.of(
                        new GhLogin("github.com", "alice", true, true),
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
        assertEquals(
                List.of("gh", "browse", "--branch", "main"), command.executed().commandLine());
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
        assertEquals(
                Option.of(12), CliGitHub.createdNumber("\nCreating pull request...\nhttps://github.com/o/r/pull/12\n"));
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
        String raw =
                "pr\t42\tAdd\\trename\tcvf\tOPEN\ttrue\tsupport_rename\tmain\t2026-10-10T08:00:00Z\t2026-10-12T09:00:00Z\t120\t34\t7"
                        + "\tMERGEABLE\tCHANGES_REQUESTED\t2\t\n"
                        + "threads\t130\t100\t2\t12\n"
                        + "label\tui\n"
                        + "review\tbob\tCHANGES_REQUESTED\n"
                        + "review\tcarol\tAPPROVED\n"
                        + "review\tdave\tDISMISSED\n"
                        + "requested\tcarol\n"
                        + "requested\tcore-team\n"
                        + "check\tbuild\tPENDING\n"
                        + "check\tci/lint\tSUCCESS\n";

        assertEquals(
                Either.right(new PullRequestDetails(
                        42,
                        "Add\trename",
                        "cvf",
                        State.OPEN,
                        true,
                        "support_rename",
                        "main",
                        Instant.parse("2026-10-10T08:00:00Z"),
                        Instant.parse("2026-10-12T09:00:00Z"),
                        120,
                        34,
                        7,
                        Mergeable.MERGEABLE,
                        Review.CHANGES_REQUESTED,
                        2,
                        new ReviewThreads(130, 100, 2, 12),
                        List.of("ui"),
                        List.of(
                                new Reviewer("bob", ReviewerState.CHANGES_REQUESTED),
                                new Reviewer("carol", ReviewerState.REQUESTED),
                                new Reviewer("core-team", ReviewerState.REQUESTED)),
                        List.of(new Check("build", Checks.PENDING), new Check("ci/lint", Checks.PASSING)))),
                CliGitHub.parseDetails(raw));
    }

    @Test
    void aMergedPullRequestWithoutChecksLabelsOrReviewers() {
        String raw =
                "pr\t5\tFix\t\tMERGED\tfalse\tfix\tmain\t2026-01-01T00:00:00Z\t2026-01-01T00:00:00Z\t1\t0\t1\tUNKNOWN\t\t0\t\n"
                        + "threads\t0\t0\t0\t0\n";

        assertEquals(
                Either.right(new PullRequestDetails(
                        5,
                        "Fix",
                        "",
                        State.MERGED,
                        false,
                        "fix",
                        "main",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        1,
                        0,
                        1,
                        Mergeable.UNKNOWN,
                        Review.NONE,
                        0,
                        ReviewThreads.NONE,
                        List.empty(),
                        List.empty(),
                        List.empty())),
                CliGitHub.parseDetails(raw));
    }

    @Test
    void detailsThatCannotBeReadAreUnexpectedOutput() {
        assertTrue(CliGitHub.parseDetails("").isLeft());
        assertTrue(CliGitHub.parseDetails("label\tui\n").isLeft());
        assertTrue(CliGitHub.parseDetails("pr\tx\n").isLeft());
        assertTrue(CliGitHub.parseDetails("something\telse\n").isLeft());
        // Without its review threads.
        assertTrue(CliGitHub.parseDetails(
                        "pr\t5\tFix\t\tMERGED\tfalse\tfix\tmain\t2026-01-01T00:00:00Z\t2026-01-01T00:00:00Z\t1\t0\t1\tUNKNOWN\t\t0\t\n")
                .isLeft());
        assertTrue(CliGitHub.parseDetails(
                        "pr\t5\tFix\t\tMERGED\tfalse\tfix\tmain\t2026-01-01T00:00:00Z\t2026-01-01T00:00:00Z\t1\t0\t1\tUNKNOWN\t\t0\t\n"
                                + "threads\tx\t0\t0\t0\n")
                .isLeft());
    }

    @Test
    void aCheckWithoutAStateYetIsNeitherPassingNorFailing() {
        String raw =
                "pr\t5\tFix\t\tOPEN\tfalse\tfix\tmain\t2026-01-01T00:00:00Z\t2026-01-01T00:00:00Z\t1\t0\t1\tUNKNOWN\t\t0\t\n"
                        + "threads\t0\t0\t0\t0\n"
                        + "check\tci/status\t\n";

        assertEquals(
                List.of(new Check("ci/status", Checks.NONE)),
                CliGitHub.parseDetails(raw).get().checks());
    }

    @Test
    void detailLinesWithTooFewFieldsAreUnexpectedOutput() {
        String pr =
                "pr\t5\tFix\t\tOPEN\tfalse\tfix\tmain\t2026-01-01T00:00:00Z\t2026-01-01T00:00:00Z\t1\t0\t1\tUNKNOWN\t\t0\t\n"
                        + "threads\t0\t0\t0\t0\n";

        assertTrue(CliGitHub.parseDetails(pr + "check\tbuild\n").isLeft());
        assertTrue(CliGitHub.parseDetails(pr + "review\tbob\n").isLeft());
        assertTrue(CliGitHub.parseDetails(pr + "label\n").isLeft());
        assertTrue(CliGitHub.parseDetails(pr + "requested\n").isLeft());
        assertTrue(CliGitHub.parseDetails(pr + "threads\t0\t0\n").isLeft());
    }

    @Test
    void parsesTheConversationOnAPullRequest() {
        String raw = "description\tdescription\tcvf\t2026-10-10T08:00:00Z\tfalse\tfalse\t\tAdds\\n**rename**\n"
                + "comment\tIC_1\tbob\t2026-10-10T09:00:00Z\ttrue\tfalse\t\tLooks\\tgood\n"
                + "CHANGES_REQUESTED\tPRR_1\tcarol\t2026-10-10T10:00:00Z\tfalse\tfalse\t\tNot yet\n"
                + "comment\tIC_2\t\t2026-10-10T11:00:00Z\tfalse\ttrue\toutdated\tOld\n"
                + "APPROVED\tPRR_2\tcarol\t2026-10-10T12:00:00Z\tfalse\tfalse\t\t\n";

        assertEquals(
                Either.right(List.of(
                        new PullRequestComment(
                                PullRequestComment.DESCRIPTION,
                                PullRequestComment.Kind.DESCRIPTION,
                                "cvf",
                                Instant.parse("2026-10-10T08:00:00Z"),
                                "Adds\n**rename**",
                                false,
                                Option.none()),
                        new PullRequestComment(
                                new PullRequestComment.Id("IC_1"),
                                PullRequestComment.Kind.COMMENT,
                                "bob",
                                Instant.parse("2026-10-10T09:00:00Z"),
                                "Looks\tgood",
                                true,
                                Option.none()),
                        new PullRequestComment(
                                new PullRequestComment.Id("PRR_1"),
                                PullRequestComment.Kind.CHANGES_REQUESTED,
                                "carol",
                                Instant.parse("2026-10-10T10:00:00Z"),
                                "Not yet",
                                false,
                                Option.none()),
                        new PullRequestComment(
                                new PullRequestComment.Id("IC_2"),
                                PullRequestComment.Kind.COMMENT,
                                "",
                                Instant.parse("2026-10-10T11:00:00Z"),
                                "Old",
                                false,
                                Option.of("outdated")),
                        new PullRequestComment(
                                new PullRequestComment.Id("PRR_2"),
                                PullRequestComment.Kind.APPROVED,
                                "carol",
                                Instant.parse("2026-10-10T12:00:00Z"),
                                "",
                                false,
                                Option.none()))),
                CliGitHub.parseComments(raw));
    }

    @Test
    void aConversationWithoutAnything() {
        assertEquals(Either.right(List.empty()), CliGitHub.parseComments(""));
    }

    @Test
    void aConversationThatCannotBeReadIsUnexpectedOutput() {
        assertTrue(CliGitHub.parseComments("comment\tIC_1\n").isLeft());
        assertTrue(CliGitHub.parseComments("PENDING\tPRR_1\tbob\t2026-10-10T09:00:00Z\tfalse\tfalse\t\tx\n")
                .isLeft());
        assertTrue(CliGitHub.parseComments("comment\tIC_1\tbob\tyesterday\tfalse\tfalse\t\tx\n")
                .isLeft());
    }

    @Test
    void anOpenPullRequestIsComparedAtItsHeadWithItsBase(@TempDir Path dir) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "no POSIX shell");

        PullRequestDetails details =
                detailsFrom(dir, "OPEN", "abc123", "echo 2").get();

        assertEquals(Option.of(2), details.behindBase());
        String asked = Files.readString(dir.resolve("compared"));
        assertTrue(asked.contains(" base=refs/heads/main "), asked);
        assertTrue(asked.contains(" head=abc123 "), asked);
    }

    @Test
    void aPullRequestThatIsNoLongerOpenOrWithoutAKnownHeadIsNotCompared(@TempDir Path dir) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "no POSIX shell");

        assertEquals(
                Option.none(),
                detailsFrom(dir, "MERGED", "abc123", "echo 2").get().behindBase());
        assertEquals(Option.none(), detailsFrom(dir, "OPEN", "", "echo 2").get().behindBase());
        assertFalse(Files.exists(dir.resolve("compared")));
    }

    @Test
    void theDetailsAreReadWhenComparingFails(@TempDir Path dir) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "no POSIX shell");

        assertEquals(
                Option.none(),
                detailsFrom(dir, "OPEN", "abc123", "exit 1").get().behindBase());
        assertEquals(
                Option.none(),
                detailsFrom(dir, "OPEN", "abc123", "echo many").get().behindBase());
    }

    @Test
    void aPullRequestIsCheckedOutInAWorktreeTakenRelativeToThisOne(@TempDir Path dir) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "no POSIX shell");
        Path main = Files.createDirectories(dir.resolve("main")).toRealPath();
        Path other = Files.createDirectories(dir.resolve("other")).toRealPath();
        // A gh that fails, telling where it ran and what with.
        Path gh = Files.writeString(dir.resolve("gh"), "#!/bin/sh\necho \"$(pwd -P) $*\" >&2\nexit 1\n");
        gh.toFile().setExecutable(true);

        Either<GhFailure, List<ExecutedCommand>> outcome =
                new CliGitHub(main, gh.toString(), GhToken.NONE).checkoutPullRequest(9, Path.of("../other"));

        assertEquals(
                new GhError.CommandFailed(Option.of(other + " pr checkout 9"), 1),
                outcome.getLeft().error());
    }
}
