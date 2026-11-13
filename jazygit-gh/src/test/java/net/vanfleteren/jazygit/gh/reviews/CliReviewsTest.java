package net.vanfleteren.jazygit.gh.reviews;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.gh.CliGitHub;
import net.vanfleteren.jazygit.gh.GitHub;
import net.vanfleteren.jazygit.gh.model.PullRequestComment;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.*;
import net.vanfleteren.jazygit.gh.model.Reaction;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.process.GhError;
import net.vanfleteren.jazygit.gh.process.GhToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The conversation and the review threads of a pull request through gh (ADR 114).
 */
class CliReviewsTest {

    @Test
    void aCommentIsAddedWithItsBodyAsTyped() {
        assertEquals(
                List.of("gh", "pr", "comment", "12", "--body", "Looks good\n\n- one nit"),
                CliReviews.commentCommand(12, "Looks good\n\n- one nit")
                        .executed()
                        .commandLine());
    }

    @Test
    void aCommentOfTheConversationIsDeletedByItsNodeId() {
        List<String> args = CliReviews.deleteCommentCommand(new PullRequestComment.Id("IC_1"))
                .executed()
                .commandLine();

        assertEquals(List.of("gh", "api", "graphql"), args.take(3));
        assertTrue(args.get(4).contains("deleteIssueComment"), args.get(4));
        assertEquals(List.of("-f", "comment=IC_1"), args.drop(5));
    }

    /*
     * A gh that fails as GitHub answers a mutation on a node it does not have, or, when not gone, as it answers one
     * the viewer may not make.
     */
    private static GitHub failing(Path dir, boolean gone) throws Exception {
        String answer = gone
                ? "echo '{\"errors\":[{\"type\":\"NOT_FOUND\"}]}'\n"
                        + "echo \"gh: Could not resolve to a node with the global id of 'IC_1'.\" >&2\n"
                : "echo 'gh: Resource not accessible by integration' >&2\n";
        Path gh = Files.writeString(dir.resolve("gh"), "#!/bin/sh\n" + answer + "exit 1\n");
        gh.toFile().setExecutable(true);
        return CliGitHub.of(dir, gh.toString(), GhToken.NONE);
    }

    @Test
    void aCommentThatIsGoneAlreadyCountsAsDeleted(@TempDir Path dir) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "no POSIX shell");
        GitHub gitHub = failing(dir, true);

        assertTrue(gitHub.reviews()
                .deleteRemark(new ReviewThread.Comment.Id("PRRC_1"))
                .isRight());
        assertTrue(gitHub.reviews()
                .deleteComment(new PullRequestComment.Id("IC_1"))
                .isRight());
    }

    @Test
    void aCommentThatCannotBeDeletedSaysWhy(@TempDir Path dir) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "no POSIX shell");

        assertEquals(
                new GhError.CommandFailed(Option.of("gh: Resource not accessible by integration"), 1),
                failing(dir, false)
                        .reviews()
                        .deleteRemark(new ReviewThread.Comment.Id("PRRC_1"))
                        .getLeft()
                        .error());
    }

    @Test
    void aCommentOfTheConversationIsEditedByItsNodeIdWithItsBodyAsTyped() {
        List<String> args = CliReviews.editCommentCommand(new PullRequestComment.Id("IC_1"), "@all see")
                .executed()
                .commandLine();

        assertEquals(List.of("gh", "api", "graphql"), args.take(3));
        assertTrue(args.get(4).contains("updateIssueComment"), args.get(4));
        assertEquals(List.of("-f", "comment=IC_1", "-f", "body=@all see"), args.drop(5));
    }

    // A gh that answers an edit with answer.
    private static GitHub answering(Path dir, String answer) throws Exception {
        Path gh = Files.writeString(dir.resolve("gh"), "#!/bin/sh\ncat <<'EOF'\n" + answer + "\nEOF\n");
        gh.toFile().setExecutable(true);
        return CliGitHub.of(dir, gh.toString(), GhToken.NONE);
    }

    @Test
    void anEditedCommentGivesTheBodyGitHubHolds(@TempDir Path dir) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "no POSIX shell");

        assertEquals(
                "Fixed",
                answering(dir, """
                                {"data": {"updateIssueComment": {"issueComment": {"body": "Fixed"}}}}""")
                        .reviews()
                        .editComment(new PullRequestComment.Id("IC_1"), "Fixed ")
                        .get()
                        .body());
        assertEquals(
                "Fixed",
                answering(dir, """
                                {"data": {"updatePullRequestReviewComment": \
                                {"pullRequestReviewComment": {"body": "Fixed"}}}}""")
                        .reviews()
                        .editRemark(new ReviewThread.Comment.Id("PRRC_1"), "Fixed ")
                        .get()
                        .body());
    }

    @Test
    void anEditOfACommentThatIsGoneFailsAsGone(@TempDir Path dir) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "no POSIX shell");
        GitHub gitHub = failing(dir, true);

        assertEquals(
                new GhError.Gone(),
                gitHub.reviews()
                        .editRemark(new ReviewThread.Comment.Id("PRRC_1"), "Fixed")
                        .getLeft()
                        .error());
        assertEquals(
                new GhError.Gone(),
                gitHub.reviews()
                        .editComment(new PullRequestComment.Id("IC_1"), "Fixed")
                        .getLeft()
                        .error());
    }

    @Test
    void aReactionToACommentThatIsGoneFailsAsGone(@TempDir Path dir) throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "no POSIX shell");

        assertEquals(
                new GhError.Gone(),
                failing(dir, true)
                        .reviews()
                        .react(new ReviewThread.Comment.Id("PRRC_1"), Reaction.EYES, true)
                        .getLeft()
                        .error());
        assertEquals(
                new GhError.CommandFailed(Option.of("gh: Resource not accessible by integration"), 1),
                failing(dir, false)
                        .reviews()
                        .react(new ReviewThread.Comment.Id("PRRC_1"), Reaction.EYES, false)
                        .getLeft()
                        .error());
    }
}
