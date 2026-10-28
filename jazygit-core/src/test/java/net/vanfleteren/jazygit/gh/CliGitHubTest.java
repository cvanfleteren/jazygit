package net.vanfleteren.jazygit.gh;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.BranchChecks;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestComment;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.*;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Activity;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Branches;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Conversation;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Size;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Status;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliGitHubTest {

    // Not line ends to a tab-separated reader, only to \R.
    private static final String LINE_SEPARATOR = Character.toString(0x2028);
    // What a gh asked how far behind its base a pull request is prints.
    private static final String BEHIND_BY_2 =
            "echo '{\"data\": {\"repository\": {\"ref\": {\"compare\": {\"behindBy\": 2}}}}}'";
    private static final String BEHIND_BY_MANY =
            "echo '{\"data\": {\"repository\": {\"ref\": {\"compare\": {\"behindBy\": \"many\"}}}}}'";
    private static final String PARAGRAPH_SEPARATOR = Character.toString(0x2029);

    // Pull request 95 on top of 94, which is merged already.
    private static final PullRequestStack STACK = new PullRequestStack(
            96,
            2,
            2,
            List.of(new PullRequestStack.Entry(1, 94, State.MERGED), new PullRequestStack.Entry(2, 95, State.OPEN)));

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
                        + "\\t\\t0\\t" + head + "\\t1\\nthreads\\t0\\t0\\t0\\t0\\n' ;;\n"
                        + "esac\n");
        gh.toFile().setExecutable(true);
        return new CliGitHub(dir, gh.toString(), GhToken.NONE).pullRequestDetails(95);
    }

    @Test
    void theDetailsOfAStackedPullRequestSayWhereItSitsAndWhatItsHeadIs() {
        String raw =
                "pr\t95\tFix\t\tOPEN\tfalse\tfix\tmain\t2026-01-01T00:00:00Z\t2026-01-01T00:00:00Z\t1\t0\t1\tUNKNOWN\t\t0"
                        + "\tabc123\t1\n"
                        + "threads\t0\t0\t0\t0\n"
                        + "stack\t96\t2\t2\t1:94:MERGED,2:95:OPEN\n";

        PullRequestDetails details = CliGitHub.parseDetails(raw).get();

        assertEquals(Option.of(new GhCommit("abc123")), details.branches().headCommit());
        assertEquals(Option.of(STACK), details.stack());
        assertEquals(Option.of(STACK), details.listed().get().stack());
        assertTrue(CliGitHub.parseDetails(raw + "stack\t96\t2\t2\n").isLeft());
        assertEquals(0, details.stack().get().below());
    }

    @Test
    void theDetailsAsReadAreNotKnownToBeBehindTheirBase() {
        assertEquals(
                Option.none(),
                CliGitHub.parseDetails(
                                "pr\t95\tFix\t\tOPEN\tfalse\tfix\tmain\t2026-01-01T00:00:00Z\t2026-01-01T00:00:00Z\t1\t0\t1"
                                        + "\tMERGEABLE\t\t0\tabc123\t1\nthreads\t0\t0\t0\t0\n")
                        .get()
                        .branches()
                        .behindBase());
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
                CliGitHub.browseCommand(new GhBranch("feature/login")).commandLine("gh"));
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
                CliGitHub.createCommand(
                                new GhBranch("feature"),
                                new GhBranch("main"),
                                new NewPullRequest("Add login", "", false))
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
                CliGitHub.createCommand(
                                new GhBranch("feature"),
                                new GhBranch("main"),
                                new NewPullRequest("Add login", "Why", true))
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
    void ghRunsFromItsExecutableButIsLoggedAsGh() {
        GhCommand command = CliGitHub.browseCommand(new GhBranch("main"));

        assertEquals("/opt/gh/bin/gh", command.commandLine("/opt/gh/bin/gh").head());
        assertEquals(
                List.of("gh", "browse", "--branch", "main"), command.executed().commandLine());
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
                        + "\tMERGEABLE\tCHANGES_REQUESTED\t2\t\t3\n"
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
                        new Status(State.OPEN, true, Mergeable.MERGEABLE),
                        new Branches("support_rename", "main"),
                        new Activity(Instant.parse("2026-10-10T08:00:00Z"), Instant.parse("2026-10-12T09:00:00Z")),
                        new Size(120, 34, 7, 3),
                        new Conversation(
                                Review.CHANGES_REQUESTED,
                                List.of(
                                        new Reviewer("bob", ReviewerState.CHANGES_REQUESTED),
                                        new Reviewer("carol", ReviewerState.REQUESTED),
                                        new Reviewer("core-team", ReviewerState.REQUESTED)),
                                2,
                                new ReviewThreads(130, 100, 2, 12),
                                List.of("ui")),
                        List.of(new Check("build", Checks.PENDING), new Check("ci/lint", Checks.PASSING)))),
                CliGitHub.parseDetails(raw));
    }

    @Test
    void aMergedPullRequestWithoutChecksLabelsOrReviewers() {
        String raw =
                "pr\t5\tFix\t\tMERGED\tfalse\tfix\tmain\t2026-01-01T00:00:00Z\t2026-01-01T00:00:00Z\t1\t0\t1\tUNKNOWN\t\t0\t\t1\n"
                        + "threads\t0\t0\t0\t0\n";

        assertEquals(
                Either.right(new PullRequestDetails(
                        5,
                        "Fix",
                        "",
                        new Status(State.MERGED, false, Mergeable.UNKNOWN),
                        new Branches("fix", "main"),
                        new Activity(Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z")),
                        new Size(1, 0, 1, 1),
                        new Conversation(Review.NONE, List.empty(), 0, ReviewThreads.NONE, List.empty()),
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
                        "pr\t5\tFix\t\tMERGED\tfalse\tfix\tmain\t2026-01-01T00:00:00Z\t2026-01-01T00:00:00Z\t1\t0\t1\tUNKNOWN\t\t0\t\t1\n")
                .isLeft());
        assertTrue(CliGitHub.parseDetails(
                        "pr\t5\tFix\t\tMERGED\tfalse\tfix\tmain\t2026-01-01T00:00:00Z\t2026-01-01T00:00:00Z\t1\t0\t1\tUNKNOWN\t\t0\t\t1\n"
                                + "threads\tx\t0\t0\t0\n")
                .isLeft());
    }

    @Test
    void aCheckWithoutAStateYetIsNeitherPassingNorFailing() {
        String raw =
                "pr\t5\tFix\t\tOPEN\tfalse\tfix\tmain\t2026-01-01T00:00:00Z\t2026-01-01T00:00:00Z\t1\t0\t1\tUNKNOWN\t\t0\t\t1\n"
                        + "threads\t0\t0\t0\t0\n"
                        + "check\tci/status\t\n";

        assertEquals(
                List.of(new Check("ci/status", Checks.NONE)),
                CliGitHub.parseDetails(raw).get().checks());
    }

    @Test
    void theChecksOfAPullRequestAreTimedAndItsRepositoryKnown() {
        String raw = "repo\tcvanfleteren/jazygit\n"
                + "pr\t5\tFix\t\tOPEN\tfalse\tfix\tmain\t2026-01-01T00:00:00Z\t2026-01-01T00:00:00Z\t1\t0\t1\tUNKNOWN\t\t0\t\t1\n"
                + "threads\t0\t0\t0\t0\n"
                + "check\tacceptance\tPENDING\t121\tBuild\t\t\t\t401\t2026-10-24T22:18:29Z\t1\tpull_request\n"
                + "check\tverify\tSUCCESS\t122\tBuild\tSUCCESS\t2026-10-24T22:18:31Z\t2026-10-24T22:20:25Z"
                + "\t401\t2026-10-24T22:18:29Z\t2\tpush\n"
                + "check\tci/status\tPENDING\t\t\t\t\t\t\t\t\t\n";

        PullRequestDetails details = CliGitHub.parseDetails(raw).get();

        assertEquals("cvanfleteren/jazygit", details.repository());
        assertEquals(
                List.of(
                        new Check(
                                "acceptance",
                                Checks.PENDING,
                                Option.of(new CheckRun(
                                        121,
                                        "Build",
                                        "",
                                        Option.none(),
                                        Option.none(),
                                        401,
                                        Instant.parse("2026-10-24T22:18:29Z"),
                                        1,
                                        "pull_request"))),
                        new Check(
                                "verify",
                                Checks.PASSING,
                                Option.of(new CheckRun(
                                        122,
                                        "Build",
                                        "SUCCESS",
                                        Option.of(Instant.parse("2026-10-24T22:18:31Z")),
                                        Option.of(Instant.parse("2026-10-24T22:20:25Z")),
                                        401,
                                        Instant.parse("2026-10-24T22:18:29Z"),
                                        2,
                                        "push"))),
                        new Check("ci/status", Checks.PENDING)),
                details.checks());
        assertTrue(details.checks().head().queued());
        assertFalse(details.checks().last().queued());
    }

    @Test
    void aCheckWhoseTimingCannotBeReadIsNotTimed() {
        assertEquals(
                new Check("verify", Checks.PENDING),
                CliGitHub.check(List.of("verify", "PENDING", "x", "Build", "", "", "", "401", "nope", "1", "push")));
    }

    @Test
    void theSuitesOfACommitAreReadWhereverItsChecksAre() {
        // Without them, a workflow that waits to start reads as no checks (ADR 35).
        List.of(
                        BranchesQuery.QUERY,
                        BranchCheckRunsQuery.query(1),
                        CliGitHub.DETAILS_QUERY,
                        PullRequestsQuery.QUERY,
                        RepositoryPullRequestsQuery.QUERY,
                        MyPullRequestsQuery.QUERY)
                .forEach(query -> assertTrue(query.contains("checkSuites(first: 20)"), query));
    }

    @Test
    void aWorkflowRunThatWaitsToStartIsAQueuedCheckOfThePullRequestNextToItsOthers() {
        String raw = "repo\tcvanfleteren/jazygit\n"
                + "pr\t42\tAdd\tcvf\tOPEN\tfalse\tfeature\tmain\t2026-10-26T22:00:00Z\t2026-10-26T22:00:00Z\t1\t2\t3"
                + "\tMERGEABLE\t\t0\tabc123\t1\n"
                + "threads\t0\t0\t0\t0\n"
                + "check\tci/status\tSUCCESS\t\t\t\t\t\t\t\t\t\n"
                + "check\tPublish\tPENDING\t0\tPublish\t\t\t\t404\t2026-10-26T23:32:02Z\t1\tpush\n";

        List<Check> checks = CliGitHub.parseDetails(raw).get().checks();

        assertEquals(new Check("ci/status", Checks.PASSING), checks.head());
        assertEquals(
                new Check(
                        "Publish",
                        Checks.PENDING,
                        Option.of(new CheckRun(
                                0,
                                "Publish",
                                "",
                                Option.none(),
                                Option.none(),
                                404,
                                Instant.parse("2026-10-26T23:32:02Z"),
                                1,
                                "push"))),
                checks.last());
        assertTrue(checks.last().queued());
    }

    @Test
    void detailLinesWithTooFewFieldsAreUnexpectedOutput() {
        String pr =
                "pr\t5\tFix\t\tOPEN\tfalse\tfix\tmain\t2026-01-01T00:00:00Z\t2026-01-01T00:00:00Z\t1\t0\t1\tUNKNOWN\t\t0\t\t1\n"
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
    void aFormFeedOrUnicodeLineSeparatorInATitleDoesNotSplitTheLine() {
        String title = "a\fb" + LINE_SEPARATOR + "c\u0085d\u000be" + PARAGRAPH_SEPARATOR + "f";

        String details = "pr\t95\t" + title
                + "\t\tOPEN\tfalse\tfix\tmain\t2026-01-01T00:00:00Z\t2026-01-01T00:00:00Z\t1\t0\t1\tUNKNOWN\t\t0"
                + "\tabc123\t1\nthreads\t0\t0\t0\t0\n";
        assertEquals(title, CliGitHub.parseDetails(details).get().title());
    }

    @Test
    void aFormFeedOrUnicodeLineSeparatorInACommentStaysInItsBody() {
        String body = "one\ftwo" + LINE_SEPARATOR + "three" + PARAGRAPH_SEPARATOR + "four";

        assertEquals(
                Either.right(List.of(new PullRequestComment(
                        new PullRequestComment.Id("IC_1"),
                        PullRequestComment.Kind.COMMENT,
                        "bob",
                        Instant.parse("2026-10-10T09:00:00Z"),
                        body,
                        false,
                        Option.none()))),
                CliGitHub.parseComments("comment\tIC_1\tbob\t2026-10-10T09:00:00Z\tfalse\tfalse\t\t" + body + "\n"));
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
                detailsFrom(dir, "OPEN", "abc123", BEHIND_BY_2).get();

        assertEquals(Option.of(2), details.branches().behindBase());
        String asked = Files.readString(dir.resolve("compared"));
        assertTrue(asked.contains(" base=refs/heads/main "), asked);
        assertTrue(asked.contains(" head=abc123 "), asked);
    }

    @Test
    void aPullRequestThatIsNoLongerOpenOrWithoutAKnownHeadIsNotCompared(@TempDir Path dir) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "no POSIX shell");

        assertEquals(
                Option.none(),
                detailsFrom(dir, "MERGED", "abc123", BEHIND_BY_2)
                        .get()
                        .branches()
                        .behindBase());
        assertEquals(
                Option.none(),
                detailsFrom(dir, "OPEN", "", BEHIND_BY_2).get().branches().behindBase());
        assertFalse(Files.exists(dir.resolve("compared")));
    }

    @Test
    void theDetailsAreReadWhenComparingFails(@TempDir Path dir) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "no POSIX shell");

        assertEquals(
                Option.none(),
                detailsFrom(dir, "OPEN", "abc123", "exit 1").get().branches().behindBase());
        assertEquals(
                Option.none(),
                detailsFrom(dir, "OPEN", "abc123", BEHIND_BY_MANY)
                        .get()
                        .branches()
                        .behindBase());
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
