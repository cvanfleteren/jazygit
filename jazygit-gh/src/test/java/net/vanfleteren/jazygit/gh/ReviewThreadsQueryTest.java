package net.vanfleteren.jazygit.gh;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.ReviewRemarks;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Comment;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Side;
import net.vanfleteren.jazygit.gh.model.ReviewThread.Subject;
import org.junit.jupiter.api.Test;

/**
 * The review threads of a pull request, from the pages GitHub answers the query of {@link ReviewThreadsQuery} with.
 */
class ReviewThreadsQueryTest {

    private static final String AT = "2026-10-10T08:00:00Z";

    private static Either<GhError.UnexpectedOutput, ReviewRemarks> read(String... pages) {
        return GhProcess.decoded(List.of(pages).mkString("[", ", ", "]"), ReviewThreadsQuery.DECODER);
    }

    private static String page(String head, String threads) {
        return """
                { "data": { "repository": { "pullRequest": { "state": "OPEN", "headRefOid": %s, "baseRefOid": "def456",
                  "reviewThreads": { "pageInfo": { "hasNextPage": false, "endCursor": null },
                  "nodes": [ %s ] } } } } }""".formatted(head, threads);
    }

    private static String thread(String place, int total, String comments) {
        return """
                { "path": "src/Foo.java", %s, "originalLine": 12, "isResolved": false, "isOutdated": false,
                  "comments": { "totalCount": %d, "nodes": [ %s ] } }""".formatted(place, total, comments);
    }

    private static String onLine(int line) {
        return """
                "diffSide": "RIGHT", "line": %d, "startLine": null, "startDiffSide": null,
                "subjectType": "LINE\"""".formatted(line);
    }

    private static String comment(String author, String state, String minimized, String body) {
        return """
                { "author": %s, "createdAt": "%s", "body": "%s", "state": "%s", "isMinimized": %b,
                  "minimizedReason": %s }""".formatted(author, AT, body, state, !minimized.equals("null"), minimized);
    }

    private static ReviewThread onLine12(List<Comment> comments, int more) {
        return new ReviewThread(
                "src/Foo.java",
                Side.RIGHT,
                Option.of(12),
                Option.none(),
                Side.RIGHT,
                Option.of(12),
                false,
                false,
                Subject.LINE,
                comments,
                more);
    }

    @Test
    void theThreadsOfAllPagesWithTheCommitsOfTheFirst() {
        Comment bob = new Comment("bob", Instant.parse(AT), "Rename this?", Option.none());
        Comment carol = new Comment("carol", Instant.parse(AT), "Agreed", Option.none());

        assertEquals(
                Either.right(new ReviewRemarks(
                        PullRequestDetails.State.OPEN,
                        Option.of(new GhCommit("abc123")),
                        Option.of(new GhCommit("def456")),
                        List.of(onLine12(List.of(bob), 0), onLine12(List.of(carol), 0)))),
                read(
                        page(
                                "\"abc123\"",
                                thread(
                                        onLine(12),
                                        1,
                                        comment("{ \"login\": \"bob\" }", "SUBMITTED", "null", "Rename this?"))),
                        page(
                                "\"abc123\"",
                                thread(
                                        onLine(12),
                                        1,
                                        comment("{ \"login\": \"carol\" }", "SUBMITTED", "null", "Agreed")))));
    }

    @Test
    void aCommentNotSubmittedYetIsLeftOutAndAThreadOfOnlyThoseToo() {
        String mine = comment("{ \"login\": \"me\" }", "PENDING", "null", "Draft");
        String theirs = comment("{ \"login\": \"bob\" }", "SUBMITTED", "null", "Done");

        assertEquals(
                List.of(onLine12(List.of(new Comment("bob", Instant.parse(AT), "Done", Option.none())), 0)),
                read(page(
                                "\"abc123\"",
                                thread(onLine(12), 2, theirs + ", " + mine) + ", " + thread(onLine(12), 1, mine)))
                        .get()
                        .threads());
    }

    @Test
    void aHiddenCommentSaysWhyAndADeletedAuthorHasNoName() {
        assertEquals(
                List.of(new Comment("", Instant.parse(AT), "spam", Option.of("SPAM"))),
                read(page("\"abc123\"", thread(onLine(12), 1, comment("null", "SUBMITTED", "\"SPAM\"", "spam"))))
                        .get()
                        .threads()
                        .head()
                        .comments());
    }

    @Test
    void aThreadWithMoreCommentsThanAreReadSaysHowManyMore() {
        assertEquals(
                49,
                read(page(
                                "\"abc123\"",
                                thread(
                                        onLine(12),
                                        50,
                                        comment("{ \"login\": \"bob\" }", "SUBMITTED", "null", "First"))))
                        .get()
                        .threads()
                        .head()
                        .more());
    }

    @Test
    void anOutdatedThreadOnSeveralLinesOfTheOldFileAndOneOnAWholeFile() {
        String outdated = """
                "diffSide": "LEFT", "line": null, "startLine": 10, "startDiffSide": "RIGHT", "subjectType": "LINE\"""";
        String onFile = """
                "diffSide": "RIGHT", "line": null, "startLine": null, "startDiffSide": null, "subjectType": "FILE\"""";
        String bob = comment("{ \"login\": \"bob\" }", "SUBMITTED", "null", "Hm");

        List<ReviewThread> threads = read(page("null", thread(outdated, 1, bob) + ", " + thread(onFile, 1, bob)))
                .get()
                .threads();

        assertEquals(Side.LEFT, threads.head().side());
        assertEquals(Option.none(), threads.head().line());
        assertEquals(Option.of(10), threads.head().startLine());
        // Its first line on the other side than its last.
        assertEquals(Side.RIGHT, threads.head().startSide());
        assertEquals(Side.RIGHT, threads.last().startSide());
        assertEquals(Subject.FILE, threads.last().subject());
    }

    @Test
    void withoutAPageThereIsNothing() {
        assertEquals(Either.right(ReviewRemarks.none()), read());
    }

    @Test
    void theQueryAsksForEveryPageOfThreadsAndAtMostFiftyCommentsEach() {
        String command = ReviewThreadsQuery.command(7).args().mkString(" ");

        assertTrue(command.contains("--paginate --slurp"), command);
        assertTrue(command.contains("number=7"), command);
        assertTrue(command.contains("reviewThreads(first: 100, after: $endCursor)"), command);
        assertTrue(command.contains("comments(first: 50)"), command);
    }
}
