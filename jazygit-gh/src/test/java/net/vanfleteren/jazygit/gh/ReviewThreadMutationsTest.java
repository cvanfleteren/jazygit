package net.vanfleteren.jazygit.gh;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.NewReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import org.junit.jupiter.api.Test;

/**
 * The commands that start a review thread, reply to one and resolve it (ADR 60).
 */
class ReviewThreadMutationsTest {

    private static List<String> args(GhCommand command) {
        return command.executed().commandLine();
    }

    @Test
    void aThreadOnOneLineGivesOnlyItsLine() {
        List<String> args = args(ReviewThreadMutations.start(
                "PR_1",
                new NewReviewThread(
                        "src/Foo.java", Option.of(new NewReviewThread.Lines(Side.RIGHT, 12, Side.RIGHT, 12)), "@me")));

        assertEquals(List.of("gh", "api", "graphql", "-f", "query=" + ReviewThreadMutations.START), args.take(5));
        assertEquals(
                List.of(
                        "-f",
                        "pullRequest=PR_1",
                        "-f",
                        "path=src/Foo.java",
                        "-f",
                        "body=@me",
                        "-F",
                        "line=12",
                        "-f",
                        "side=RIGHT",
                        "-f",
                        "subject=LINE"),
                args.drop(5));
    }

    @Test
    void aThreadOnSeveralLinesGivesItsFirstToo() {
        List<String> args = args(ReviewThreadMutations.start(
                "PR_1",
                new NewReviewThread(
                        "src/Foo.java", Option.of(new NewReviewThread.Lines(Side.LEFT, 10, Side.RIGHT, 12)), "Hm")));

        assertEquals(
                List.of("-F", "line=12", "-f", "side=RIGHT", "-F", "startLine=10", "-f", "startSide=LEFT"),
                args.drop(11).dropRight(2));
    }

    @Test
    void aThreadOnAWholeFileHasNoLine() {
        List<String> args =
                args(ReviewThreadMutations.start("PR_1", new NewReviewThread("src/Foo.java", Option.none(), "Split")));

        assertEquals(List.of("-f", "subject=FILE"), args.drop(11));
    }

    @Test
    void aReplyAndAResolveNameTheThread() {
        assertEquals(
                List.of("-f", "thread=PRRT_1", "-f", "body=Done"),
                args(ReviewThreadMutations.reply("PRRT_1", "Done")).drop(5));
        assertFalse(args(ReviewThreadMutations.resolve("PRRT_1", true)).get(4).contains("unresolve"));
        assertTrue(args(ReviewThreadMutations.resolve("PRRT_1", false)).get(4).contains("unresolveReviewThread"));
        assertEquals(
                List.of("-f", "thread=PRRT_1"),
                args(ReviewThreadMutations.resolve("PRRT_1", true)).drop(5));
    }

    @Test
    void theIdOfTheThreadStartedIsRead() {
        assertEquals(
                Either.right("PRRT_9"),
                GhProcess.decoded(
                        "{ \"data\": { \"addPullRequestReviewThread\": { \"thread\": { \"id\": \"PRRT_9\" } } } }",
                        ReviewThreadMutations.STARTED));
    }
}
