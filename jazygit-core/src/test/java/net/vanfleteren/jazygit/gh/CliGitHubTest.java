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
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestComment;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.*;
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
                        + "  *) cat <<'EOF'\n" + DetailsQueryTest.details(95, state, head) + "\nEOF\n ;;\n"
                        + "esac\n");
        gh.toFile().setExecutable(true);
        return new CliGitHub(dir, gh.toString(), GhToken.NONE).pullRequestDetails(95);
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
    void theSuitesOfACommitAreReadWhereverItsChecksAre() {
        // Without them, a workflow that waits to start reads as no checks (ADR 35).
        List.of(
                        BranchesQuery.QUERY,
                        BranchCheckRunsQuery.query(1),
                        DetailsQuery.QUERY,
                        PullRequestsQuery.QUERY,
                        RepositoryPullRequestsQuery.QUERY,
                        MyPullRequestsQuery.QUERY)
                .forEach(query -> assertTrue(query.contains("checkSuites(first: 20)"), query));
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
