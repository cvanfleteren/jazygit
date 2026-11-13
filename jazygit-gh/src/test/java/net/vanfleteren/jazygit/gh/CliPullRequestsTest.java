package net.vanfleteren.jazygit.gh;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.gh.model.GhBranch;
import net.vanfleteren.jazygit.gh.model.NewPullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.*;
import net.vanfleteren.jazygit.gh.model.PullRequestEdit;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The pull requests through gh (ADR 114).
 */
class CliPullRequestsTest {

    // How far behind its base GitHub says a pull request is, in its details.
    private static final String BEHIND_BY_2 = "{ \"compare\": { \"behindBy\": 2 } }";

    /*
     * The details of a pull request in state, at commit head, into main, with baseRef as GitHub answers it, as read
     * through a gh that writes what it was asked with to the file "asked" in dir.
     */
    private static Either<GhError, PullRequestDetails> detailsFrom(Path dir, String state, String head, String baseRef)
            throws Exception {
        String answer = DetailsQueryTest.details(95, state, head)
                .replace("\"baseRef\": " + BEHIND_BY_2, "\"baseRef\": " + baseRef);
        Path gh = Files.writeString(
                dir.resolve("gh"),
                "#!/bin/sh\n"
                        + "echo \" $* \" > '" + dir.resolve("asked") + "'\n"
                        + "cat <<'EOF'\n" + answer + "\nEOF\n");
        gh.toFile().setExecutable(true);
        return CliGitHub.of(dir, gh.toString(), GhToken.NONE).pullRequests().details(95);
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
                CliPullRequests.createCommand(
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
                CliPullRequests.createCommand(
                                new GhBranch("feature"),
                                new GhBranch("main"),
                                new NewPullRequest("Add login", "Why", true))
                        .executed()
                        .commandLine());
    }

    @Test
    void anEditOnlyPassesWhatItChanges() {
        assertEquals(
                List.of("gh", "pr", "edit", "12", "--body", ""),
                CliPullRequests.editCommand(12, new PullRequestEdit(Option.none(), Option.of(""), Option.none()))
                        .executed()
                        .commandLine());
        assertEquals(
                List.of("gh", "pr", "edit", "12", "--title", "Add login", "--body", "Why\n", "--base", "release"),
                CliPullRequests.editCommand(
                                12,
                                new PullRequestEdit(
                                        Option.of("Add login"), Option.of("Why\n"), Option.of(new GhBranch("release"))))
                        .executed()
                        .commandLine());
    }

    @Test
    void theNumberOfACreatedPullRequestIsInTheUrlGhPrints() {
        assertEquals(
                Option.of(12),
                CliPullRequests.createdNumber("\nCreating pull request...\nhttps://github.com/o/r/pull/12\n"));
        assertEquals(Option.none(), CliPullRequests.createdNumber("nothing useful"));
    }

    @Test
    void readsTheReviewDecision() {
        assertEquals(Review.REQUIRED, CliPullRequests.review("REVIEW_REQUIRED"));
        assertEquals(Review.APPROVED, CliPullRequests.review("APPROVED"));
        assertEquals(Review.CHANGES_REQUESTED, CliPullRequests.review("CHANGES_REQUESTED"));
        assertEquals(Review.NONE, CliPullRequests.review(""));
    }

    @Test
    void anOpenPullRequestIsComparedAtItsHeadWithItsBaseInTheSameQuery(@TempDir Path dir) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "no POSIX shell");

        PullRequestDetails details =
                detailsFrom(dir, "OPEN", "abc123", BEHIND_BY_2).get();

        assertEquals(Option.of(2), details.branches().behindBase());
        String asked = Files.readString(dir.resolve("asked"));
        assertTrue(asked.contains(" pullHead=refs/pull/95/head "), asked);
    }

    @Test
    void aPullRequestThatIsNoLongerOpenOrWithoutAKnownHeadIsNotBehind(@TempDir Path dir) throws Exception {
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
    }

    @Test
    void theDetailsAreReadWithoutHowFarBehindWhenGitHubCannotCompare(@TempDir Path dir) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "no POSIX shell");
        String answer = DetailsQueryTest.details(95, "OPEN", "abc123")
                .replace("\"baseRef\": " + BEHIND_BY_2, "\"baseRef\": { \"name\": \"main\" }");
        // An error for the comparison fails the whole query, as gh exits with 1.
        Path gh = Files.writeString(
                dir.resolve("gh"),
                "#!/bin/sh\n"
                        + "case \"$*\" in\n"
                        + "  *pullHead=*) echo '{\"errors\": [{\"message\": \"cannot compare\"}]}'; exit 1 ;;\n"
                        + "  *) cat <<'EOF'\n" + answer + "\nEOF\n ;;\n"
                        + "esac\n");
        gh.toFile().setExecutable(true);

        PullRequestDetails details = CliGitHub.of(dir, gh.toString(), GhToken.NONE)
                .pullRequests()
                .details(95)
                .get();

        assertEquals(95, details.number());
        assertEquals(Option.none(), details.branches().behindBase());
    }

    @Test
    void theDetailsAreReadWhenTheyCannotTellHowFarBehind(@TempDir Path dir) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "no POSIX shell");

        assertEquals(
                Option.none(),
                detailsFrom(dir, "OPEN", "abc123", "null").get().branches().behindBase());
        assertEquals(
                Option.none(),
                detailsFrom(dir, "OPEN", "abc123", "{ \"compare\": { \"behindBy\": \"many\" } }")
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
                CliGitHub.of(main, gh.toString(), GhToken.NONE).pullRequests().checkout(9, Path.of("../other"));

        assertEquals(
                new GhError.CommandFailed(Option.of(other + " pr checkout 9"), 1),
                outcome.getLeft().error());
    }
}
