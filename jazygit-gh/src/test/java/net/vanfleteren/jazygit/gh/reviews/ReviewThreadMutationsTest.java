package net.vanfleteren.jazygit.gh.reviews;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.NewReviewThread;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.model.Reaction;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.gh.process.GhCommand;
import org.junit.jupiter.api.Test;

/**
 * The commands that start a review thread, reply to one, resolve it, and edit or delete a comment of it (ADR 60,
 * ADR 64, ADR 65): the first two as REST's review comments, which GitHub publishes at once, not as a pending review.
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
                        new PrNumber(7),
                        new GhCommit("abc123"),
                        new NewReviewThread(
                                "src/Foo.java",
                                Option.of(new NewReviewThread.Lines(Side.RIGHT, 12, Side.RIGHT, 12)),
                                "@me"))));
    }

    @Test
    void aThreadOnSeveralLinesGivesItsFirstToo() {
        List<String> args = args(ReviewThreadMutations.start(
                new PrNumber(7),
                new GhCommit("abc123"),
                new NewReviewThread(
                        "src/Foo.java", Option.of(new NewReviewThread.Lines(Side.LEFT, 10, Side.RIGHT, 12)), "Hm")));

        assertEquals(
                List.of("-F", "line=12", "-f", "side=RIGHT", "-F", "start_line=10", "-f", "start_side=LEFT"),
                args.drop(11));
    }

    @Test
    void aThreadOnAWholeFileHasNoLine() {
        List<String> args = args(ReviewThreadMutations.start(
                new PrNumber(7), new GhCommit("abc123"), new NewReviewThread("src/Foo.java", Option.none(), "Split")));

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
                args(ReviewThreadMutations.reply(new PrNumber(7), new ReviewThread.Comment.RestId("101"), "Done")));
    }

    @Test
    void aThreadIsResolvedOrUnresolvedByItsNodeId() {
        assertFalse(args(ReviewThreadMutations.resolve(new ReviewThread.Id("PRRT_1"), true))
                .get(4)
                .contains("unresolve"));
        assertTrue(args(ReviewThreadMutations.resolve(new ReviewThread.Id("PRRT_1"), false))
                .get(4)
                .contains("unresolveReviewThread"));
        assertEquals(
                List.of("-f", "thread=PRRT_1"),
                args(ReviewThreadMutations.resolve(new ReviewThread.Id("PRRT_1"), true))
                        .drop(5));
    }

    @Test
    void aCommentIsDeletedByItsNodeId() {
        List<String> args = args(ReviewThreadMutations.delete(new ReviewThread.Comment.Id("PRRC_1")));

        assertEquals(List.of("gh", "api", "graphql"), args.take(3));
        assertTrue(args.get(4).contains("deletePullRequestReviewComment"), args.get(4));
        assertEquals(List.of("-f", "comment=PRRC_1"), args.drop(5));
    }

    @Test
    void aCommentIsEditedByItsNodeIdWithItsBodyAsTyped() {
        List<String> args =
                args(ReviewThreadMutations.edit(new ReviewThread.Comment.Id("PRRC_1"), "@all see\n\n- this"));

        assertEquals(List.of("gh", "api", "graphql"), args.take(3));
        assertTrue(args.get(4).contains("updatePullRequestReviewComment"), args.get(4));
        assertEquals(List.of("-f", "comment=PRRC_1", "-f", "body=@all see\n\n- this"), args.drop(5));
    }

    @Test
    void anEditedCommentGivesTheBodyGitHubHolds() {
        assertEquals("Fixed", ReviewThreadMutations.EDITED.parse("""
                                {"data": {"updatePullRequestReviewComment": {"pullRequestReviewComment": {"body": "Fixed"}}}}""").get());
    }

    @Test
    void aReactionIsGivenOrTakenBackByTheNodeIdOfTheComment() {
        List<String> given =
                args(ReviewThreadMutations.react(new ReviewThread.Comment.Id("PRRC_1"), Reaction.THUMBS_UP, true));
        List<String> taken =
                args(ReviewThreadMutations.react(new ReviewThread.Comment.Id("PRRC_1"), Reaction.THUMBS_UP, false));

        assertEquals(
                List.of(
                        "gh",
                        "api",
                        "graphql",
                        "-f",
                        "query=" + ReviewThreadMutations.ADD_REACTION,
                        "-f",
                        "subject=PRRC_1",
                        "-f",
                        "content=THUMBS_UP"),
                given);
        assertEquals("query=" + ReviewThreadMutations.REMOVE_REACTION, taken.get(4));
    }
}
