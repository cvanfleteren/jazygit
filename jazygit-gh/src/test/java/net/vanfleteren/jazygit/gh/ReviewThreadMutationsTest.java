package net.vanfleteren.jazygit.gh;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.NewReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import org.junit.jupiter.api.Test;

/**
 * The commands that start a review thread, reply to one, resolve it and delete a comment of it (ADR 60, ADR 64): the
 * first two as REST's review comments, which GitHub publishes at once, not as a pending review.
 */
class ReviewThreadMutationsTest {

    private static final String START = "repos/{owner}/{repo}/pulls/7/comments";

    private static List<String> args(GhCommand command) {
        return command.executed().commandLine();
    }

    @Test
    void aThreadOnOneLineGivesOnlyItsLineAtTheHeadCommit() {
        assertEquals(
                List.of(
                        "gh",
                        "api",
                        "--method",
                        "POST",
                        START,
                        "-f",
                        "body=@me",
                        "-f",
                        "commit_id=abc123",
                        "-f",
                        "path=src/Foo.java",
                        "-F",
                        "line=12",
                        "-f",
                        "side=RIGHT"),
                args(ReviewThreadMutations.start(
                        7,
                        "abc123",
                        new NewReviewThread(
                                "src/Foo.java",
                                Option.of(new NewReviewThread.Lines(Side.RIGHT, 12, Side.RIGHT, 12)),
                                "@me"))));
    }

    @Test
    void aThreadOnSeveralLinesGivesItsFirstToo() {
        List<String> args = args(ReviewThreadMutations.start(
                7,
                "abc123",
                new NewReviewThread(
                        "src/Foo.java", Option.of(new NewReviewThread.Lines(Side.LEFT, 10, Side.RIGHT, 12)), "Hm")));

        assertEquals(
                List.of("-F", "line=12", "-f", "side=RIGHT", "-F", "start_line=10", "-f", "start_side=LEFT"),
                args.drop(11));
    }

    @Test
    void aThreadOnAWholeFileHasNoLine() {
        List<String> args = args(
                ReviewThreadMutations.start(7, "abc123", new NewReviewThread("src/Foo.java", Option.none(), "Split")));

        assertEquals(List.of("-f", "subject_type=file"), args.drop(11));
    }

    @Test
    void aReplyGoesToTheFirstCommentOfItsThread() {
        assertEquals(
                List.of(
                        "gh",
                        "api",
                        "--method",
                        "POST",
                        "repos/{owner}/{repo}/pulls/7/comments/101/replies",
                        "-f",
                        "body=Done"),
                args(ReviewThreadMutations.reply(7, "101", "Done")));
    }

    @Test
    void aThreadIsResolvedOrUnresolvedByItsNodeId() {
        assertFalse(args(ReviewThreadMutations.resolve("PRRT_1", true)).get(4).contains("unresolve"));
        assertTrue(args(ReviewThreadMutations.resolve("PRRT_1", false)).get(4).contains("unresolveReviewThread"));
        assertEquals(
                List.of("-f", "thread=PRRT_1"),
                args(ReviewThreadMutations.resolve("PRRT_1", true)).drop(5));
    }

    @Test
    void aCommentIsDeletedByItsNodeId() {
        List<String> args = args(ReviewThreadMutations.delete("PRRC_1"));

        assertEquals(List.of("gh", "api", "graphql"), args.take(3));
        assertTrue(args.get(4).contains("deletePullRequestReviewComment"), args.get(4));
        assertEquals(List.of("-f", "comment=PRRC_1"), args.drop(5));
    }
}
