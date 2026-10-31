package net.vanfleteren.jazygit.gh;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.CommentRights;
import net.vanfleteren.jazygit.gh.model.PullRequestComment;
import net.vanfleteren.jazygit.gh.model.PullRequestComment.Kind;
import org.junit.jupiter.api.Test;

/**
 * The conversation on a pull request, from what {@code gh pr view --json body,author,createdAt,comments,reviews}
 * prints.
 */
class CommentsQueryTest {

    private static Either<GhError.UnexpectedOutput, List<PullRequestComment>> conversation(
            String body, String comments, String reviews) {
        return GhProcess.decoded("""
                { "body": %s, "author": { "login": "cvf" }, "createdAt": "2026-10-10T08:00:00Z",
                  "comments": [ %s ], "reviews": [ %s ] }""".formatted(body, comments, reviews), CommentsQuery.DECODER);
    }

    private static String comment(String id, String author, String at, boolean edited, String minimized, String body) {
        return comment(id, author, at, edited, minimized, body, false);
    }

    private static String comment(
            String id, String author, String at, boolean edited, String minimized, String body, boolean mine) {
        return """
                { "id": "%s", "author": %s, "createdAt": "%s", "includesCreatedEdit": %b,
                  "isMinimized": %b, "minimizedReason": %s, "body": %s, "viewerDidAuthor": %b }""".formatted(id, author, at, edited, !minimized.equals("null"), minimized, body, mine);
    }

    private static String review(String id, String state, String at, String body) {
        return """
                { "id": "%s", "author": { "login": "carol" }, "state": "%s", "submittedAt": %s,
                  "includesCreatedEdit": false, "body": "%s" }""".formatted(id, state, at, body);
    }

    @Test
    void theDescriptionFirstThenTheCommentsAndReviewsOldestFirst() {
        assertEquals(
                Either.right(List.of(
                        new PullRequestComment(
                                PullRequestComment.DESCRIPTION,
                                Kind.DESCRIPTION,
                                "cvf",
                                Instant.parse("2026-10-10T08:00:00Z"),
                                "Adds\n**rename**",
                                false,
                                Option.none(),
                                CommentRights.NONE),
                        new PullRequestComment(
                                new PullRequestComment.Id("IC_1"),
                                Kind.COMMENT,
                                "bob",
                                Instant.parse("2026-10-10T09:00:00Z"),
                                "Looks\tgood",
                                true,
                                Option.none(),
                                CommentRights.ALL),
                        new PullRequestComment(
                                new PullRequestComment.Id("PRR_1"),
                                Kind.CHANGES_REQUESTED,
                                "carol",
                                Instant.parse("2026-10-10T10:00:00Z"),
                                "Not yet",
                                false,
                                Option.none(),
                                CommentRights.NONE),
                        new PullRequestComment(
                                new PullRequestComment.Id("IC_2"),
                                Kind.COMMENT,
                                "",
                                Instant.parse("2026-10-10T11:00:00Z"),
                                "Old",
                                false,
                                Option.of("outdated"),
                                CommentRights.NONE),
                        new PullRequestComment(
                                new PullRequestComment.Id("PRR_2"),
                                Kind.APPROVED,
                                "carol",
                                Instant.parse("2026-10-10T12:00:00Z"),
                                "",
                                false,
                                Option.none(),
                                CommentRights.NONE))),
                conversation(
                        "\"Adds\\n**rename**\"",
                        comment("IC_2", "null", "2026-10-10T11:00:00Z", false, "\"outdated\"", "\"Old\"", false)
                                + ", "
                                + comment(
                                        "IC_1",
                                        "{ \"login\": \"bob\" }",
                                        "2026-10-10T09:00:00Z",
                                        true,
                                        "null",
                                        "\"Looks\\tgood\"",
                                        true),
                        review("PRR_2", "APPROVED", "\"2026-10-10T12:00:00Z\"", "") + ", "
                                + review("PRR_1", "CHANGES_REQUESTED", "\"2026-10-10T10:00:00Z\"", "Not yet")));
    }

    @Test
    void aPendingReviewOrOneThatOnlyCommentedWithoutTextIsLeftOut() {
        assertEquals(
                Either.right(List.of(new PullRequestComment(
                        new PullRequestComment.Id("PRR_3"),
                        Kind.COMMENTED,
                        "carol",
                        Instant.parse("2026-10-10T12:00:00Z"),
                        "Nit",
                        false,
                        Option.none(),
                        CommentRights.NONE))),
                conversation(
                        "\"\"",
                        "",
                        review("PRR_1", "PENDING", "null", "Draft") + ", "
                                + review("PRR_2", "COMMENTED", "\"2026-10-10T11:00:00Z\"", "") + ", "
                                + review("PRR_3", "COMMENTED", "\"2026-10-10T12:00:00Z\"", "Nit")));
    }

    @Test
    void aFormFeedOrUnicodeLineSeparatorInACommentStaysInItsBody() {
        assertEquals(
                "one\ftwo three four",
                conversation(
                                "\"\"",
                                comment(
                                        "IC_1",
                                        "{ \"login\": \"bob\" }",
                                        "2026-10-10T09:00:00Z",
                                        false,
                                        "null",
                                        "\"one\\ftwo\\u2028three\\u2029four\""),
                                "")
                        .get()
                        .head()
                        .body());
    }

    @Test
    void aDismissedReviewIsKept() {
        assertEquals(
                Either.right(List.of(Kind.DISMISSED)),
                conversation("\"\"", "", review("PRR_1", "DISMISSED", "\"2026-10-10T12:00:00Z\"", ""))
                        .map(comments -> comments.map(PullRequestComment::kind)));
    }

    @Test
    void aReviewByADeletedAccountIsByNoName() {
        assertEquals(
                Either.right(List.of("")),
                conversation("\"\"", "", """
                                { "id": "PRR_1", "author": null, "state": "APPROVED",
                                  "submittedAt": "2026-10-10T12:00:00Z", "includesCreatedEdit": false, "body": "" }""").map(comments -> comments.map(PullRequestComment::author)));
    }

    @Test
    void aMinimizedCommentWithoutAReasonIsHiddenForNoReasonGiven() {
        assertEquals(
                Either.right(List.of(Option.of(""))),
                conversation("\"\"", """
                                { "id": "IC_1", "author": { "login": "bob" }, "createdAt": "2026-10-10T09:00:00Z",
                                  "includesCreatedEdit": false, "isMinimized": true, "minimizedReason": null,
                                  "body": "Spam", "viewerDidAuthor": false }""", "").map(comments -> comments.map(PullRequestComment::hidden)));
    }

    @Test
    void aCommentAndAReviewWrittenAtTheSameTimeTheCommentFirst() {
        assertEquals(
                Either.right(List.of("IC_1", "PRR_1")),
                conversation(
                                "\"\"",
                                comment(
                                        "IC_1",
                                        "{ \"login\": \"bob\" }",
                                        "2026-10-10T12:00:00Z",
                                        false,
                                        "null",
                                        "\"First\""),
                                review("PRR_1", "APPROVED", "\"2026-10-10T12:00:00Z\"", "Same time"))
                        .map(comments -> comments.map(comment -> comment.id().value())));
    }

    @Test
    void aConversationWithoutAnything() {
        assertEquals(Either.right(List.empty()), conversation("\"\"", "", ""));
    }

    @Test
    void aConversationThatCannotBeReadIsUnexpected() {
        assertEquals(
                Either.left(
                        new GhError.UnexpectedOutput("comments[0].createdAt: expected an instant, got \"yesterday\"")),
                conversation(
                        "\"\"", comment("IC_1", "{ \"login\": \"bob\" }", "yesterday", false, "null", "\"x\""), ""));
        assertEquals(
                Either.left(
                        new GhError.UnexpectedOutput(
                                "reviews[0]: expected APPROVED, CHANGES_REQUESTED, COMMENTED, DISMISSED or PENDING, got \"LGTM\"")),
                conversation("\"\"", "", review("PRR_1", "LGTM", "\"2026-10-10T12:00:00Z\"", "x")));
    }
}
