package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GitHubAnswers.VERIFY;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.VERIFY_PASSED;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.WAITING;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.WAITING_SUITES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.Tuple;
import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Activity;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Branches;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Conversation;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Mergeable;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.ReviewThreads;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Reviewer;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.ReviewerState;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Size;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Status;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import org.junit.jupiter.api.Test;

/**
 * Everything about one pull request, from what GitHub answers the query of {@link DetailsQuery}.
 */
class DetailsQueryTest {

    private static final String JANUARY = "\"2026-01-01T00:00:00Z\"";
    private static final String NO_SUITES = "\"checkSuites\": { \"nodes\": [] }";

    // The fields of a merged pull request without checks, labels or reviewers, by an account that is gone.
    private static final LinkedHashMap<String, String> MERGED = LinkedHashMap.ofEntries(
            Tuple.of("number", "5"),
            Tuple.of("title", "\"Fix\""),
            Tuple.of("author", "null"),
            Tuple.of("state", "\"MERGED\""),
            Tuple.of("isDraft", "false"),
            Tuple.of("headRefName", "\"fix\""),
            Tuple.of("baseRefName", "\"main\""),
            Tuple.of("headRefOid", "\"\""),
            Tuple.of("stackEntry", "null"),
            Tuple.of("stack", "null"),
            Tuple.of("createdAt", JANUARY),
            Tuple.of("additions", "1"),
            Tuple.of("deletions", "0"),
            Tuple.of("changedFiles", "1"),
            Tuple.of("mergeable", "\"UNKNOWN\""),
            Tuple.of("reviewDecision", "null"),
            Tuple.of("comments", "{ \"totalCount\": 0 }"),
            Tuple.of("labels", "{ \"nodes\": [] }"),
            Tuple.of("latestReviews", "{ \"nodes\": [] }"),
            Tuple.of("reviewRequests", "{ \"nodes\": [] }"),
            Tuple.of("timelineItems", "{ \"nodes\": [] }"),
            Tuple.of("commits", commits(1, JANUARY, "null", NO_SUITES)),
            Tuple.of("reviewThreads", "{ \"totalCount\": 0, \"nodes\": [] }"));

    /**
     * What GitHub answers for a pull request of {@code fields}, of cvanfleteren/jazygit.
     */
    private static String answer(LinkedHashMap<String, String> fields) {
        return "{ \"data\": { \"repository\": { \"nameWithOwner\": \"cvanfleteren/jazygit\", \"pullRequest\": "
                + fields.map(field -> "\"" + field._1() + "\": " + field._2()).mkString("{ ", ", ", " }")
                + " } } }";
    }

    /**
     * What GitHub answers for pull request {@code number} into main, in {@code state}, at commit {@code head}, empty
     * when not known.
     */
    static String details(int number, String state, String head) {
        return answer(MERGED.put("number", String.valueOf(number))
                .put("state", "\"" + state + "\"")
                .put("headRefOid", "\"" + head + "\"")
                .put("mergeable", "\"MERGEABLE\""));
    }

    // The commits of a pull request: how many there are, and the last one with its rollup and suites.
    private static String commits(int count, String committedDate, String rollup, String suites) {
        return """
                { "totalCount": %d, "nodes": [ { "commit": { "committedDate": %s, "statusCheckRollup": %s, %s } } ] }""".formatted(count, committedDate, rollup, suites);
    }

    private static String rollup(String nodes) {
        return "{ \"contexts\": { \"nodes\": [ " + nodes + " ] } }";
    }

    private static Either<GhError.UnexpectedOutput, PullRequestDetails> read(LinkedHashMap<String, String> fields) {
        return GhProcess.decoded(answer(fields), DetailsQuery.DECODER);
    }

    @Test
    void theDetailsOfAPullRequest() {
        String threads = List.range(0, 100)
                .map(i -> "{ \"isResolved\": " + (i >= 2) + ", \"comments\": { \"totalCount\": " + (i < 12 ? 1 : 0)
                        + " } }")
                .mkString("{ \"totalCount\": 130, \"nodes\": [ ", ", ", " ] }");

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
                        List.of(new Check("build", Checks.PENDING), new Check("ci/lint", Checks.PASSING)),
                        Option.none(),
                        "cvanfleteren/jazygit")),
                read(MERGED.put("number", "42")
                        .put("title", "\"Add\\trename\"")
                        .put("author", "{ \"login\": \"cvf\" }")
                        .put("state", "\"OPEN\"")
                        .put("isDraft", "true")
                        .put("headRefName", "\"support_rename\"")
                        .put("createdAt", "\"2026-10-10T08:00:00Z\"")
                        .put("additions", "120")
                        .put("deletions", "34")
                        .put("changedFiles", "7")
                        .put("mergeable", "\"MERGEABLE\"")
                        .put("reviewDecision", "\"CHANGES_REQUESTED\"")
                        .put("comments", "{ \"totalCount\": 2 }")
                        .put("labels", "{ \"nodes\": [ { \"name\": \"ui\" } ] }")
                        .put("latestReviews", """
                                { "nodes": [ { "author": { "login": "bob" }, "state": "CHANGES_REQUESTED" },
                                  { "author": { "login": "carol" }, "state": "APPROVED" },
                                  { "author": { "login": "dave" }, "state": "DISMISSED" } ] }""")
                        .put("reviewRequests", """
                                { "nodes": [ { "requestedReviewer": { "__typename": "User", "login": "carol" } },
                                  { "requestedReviewer": { "__typename": "Team", "name": "core-team" } } ] }""")
                        .put("commits", commits(3, "\"2026-10-12T09:00:00Z\"", rollup("""
                                                { "__typename": "StatusContext", "context": "build", "state": "PENDING" },
                                                { "__typename": "StatusContext", "context": "ci/lint", "state": "SUCCESS" }"""), NO_SUITES))
                        .put("reviewThreads", threads)));
    }

    @Test
    void aMergedPullRequestWithoutChecksLabelsOrReviewers() {
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
                        List.empty(),
                        Option.none(),
                        "cvanfleteren/jazygit")),
                read(MERGED));
    }

    @Test
    void itWasLastPushedToWhenItWasOpenedItsLatestCommitOrItsLatestForcePushWhicheverIsLatest() {
        LinkedHashMap<String, String> forcePushed = MERGED.put("timelineItems", """
                { "nodes": [ { "__typename": "HeadRefForcePushedEvent", "createdAt": "2026-03-01T00:00:00Z" } ] }""");

        assertEquals(
                Instant.parse("2026-03-01T00:00:00Z"),
                read(forcePushed).get().activity().pushedAt());
        assertEquals(
                Instant.parse("2026-04-01T00:00:00Z"),
                read(forcePushed.put("commits", commits(1, "\"2026-04-01T00:00:00Z\"", "null", NO_SUITES)))
                        .get()
                        .activity()
                        .pushedAt());
        // A commit made before it was opened was pushed by then.
        assertEquals(
                Instant.parse("2026-01-01T00:00:00Z"),
                read(MERGED.put("commits", commits(1, "\"2025-12-01T00:00:00Z\"", "null", NO_SUITES)))
                        .get()
                        .activity()
                        .pushedAt());
    }

    @Test
    void aTimelineItemOfAnotherTypeIsNoForcePush() {
        assertEquals(
                Instant.parse("2026-01-01T00:00:00Z"),
                read(MERGED.put("timelineItems", "{ \"nodes\": [ { \"__typename\": \"Other\" } ] }"))
                        .get()
                        .activity()
                        .pushedAt());
    }

    @Test
    void withoutCommitsItWasLastPushedToWhenItWasOpenedAndHasNoChecks() {
        PullRequestDetails details = read(MERGED.put("commits", "{ \"totalCount\": 0, \"nodes\": [] }"))
                .get();

        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), details.activity().pushedAt());
        assertEquals(List.empty(), details.checks());
    }

    @Test
    void theChecksOfAPullRequestAreTimedWithTheWorkflowRunsThatWaitToStart() {
        List<Check> checks = read(MERGED.put(
                        "commits",
                        commits(
                                1,
                                JANUARY,
                                rollup(
                                        VERIFY_PASSED + ", "
                                                + "{ \"__typename\": \"StatusContext\", \"context\": \"ci/status\", \"state\": null }"),
                                WAITING_SUITES)))
                .get()
                .checks();

        assertEquals(List.of(VERIFY, new Check("ci/status", Checks.NONE), WAITING), checks);
        assertTrue(checks.last().queued());
    }

    @Test
    void aStackedPullRequestSaysWhereItSitsAndWhatItsHeadIs() {
        PullRequestDetails details = read(MERGED.put("state", "\"OPEN\"")
                        .put("headRefOid", "\"abc123\"")
                        .put("stackEntry", "{ \"position\": 2 }")
                        .put("stack", """
                                { "number": 96, "size": 2, "entries": { "nodes": [
                                  { "position": 1, "pullRequest": { "number": 94, "state": "MERGED" } },
                                  { "position": 2, "pullRequest": { "number": 95, "state": "OPEN" } } ] } }"""))
                .get();
        PullRequestStack stack = new PullRequestStack(
                96,
                2,
                2,
                List.of(
                        new PullRequestStack.Entry(1, 94, State.MERGED),
                        new PullRequestStack.Entry(2, 95, State.OPEN)));

        assertEquals(Option.of(new GhCommit("abc123")), details.branches().headCommit());
        assertEquals(Option.of(stack), details.stack());
        assertEquals(Option.of(stack), details.listed().get().stack());
        assertEquals(0, details.stack().get().below());
    }

    @Test
    void theDetailsAsReadAreNotKnownToBeBehindTheirBase() {
        assertEquals(
                Option.none(),
                read(MERGED.put("headRefOid", "\"abc123\"")).get().branches().behindBase());
    }

    @Test
    void aReviewByADeletedAccountIsByNoName() {
        assertEquals(
                List.of(new Reviewer("", ReviewerState.APPROVED)),
                read(MERGED.put("latestReviews", """
                                { "nodes": [ { "author": null, "state": "APPROVED" } ] }""")).get().conversation().reviewers());
    }

    @Test
    void aBotOrAMannequinIsAskedForByItsLogin() {
        assertEquals(
                List.of(
                        new Reviewer("dependabot", ReviewerState.REQUESTED),
                        new Reviewer("old-cvs", ReviewerState.REQUESTED)),
                read(MERGED.put("reviewRequests", """
                                { "nodes": [ { "requestedReviewer": { "__typename": "Bot", "login": "dependabot" } },
                                  { "requestedReviewer": { "__typename": "Mannequin", "login": "old-cvs" } } ] }""")).get().conversation().reviewers());
    }

    @Test
    void aReviewerOfAnotherTypeIsAskedForWithoutAName() {
        assertEquals(
                List.of(new Reviewer("", ReviewerState.REQUESTED), new Reviewer("", ReviewerState.REQUESTED)),
                read(MERGED.put("reviewRequests", """
                                { "nodes": [ { "requestedReviewer": { "__typename": "Organization" } },
                                  { "requestedReviewer": null } ] }""")).get().conversation().reviewers());
    }

    @Test
    void aTitleKeepsWhatWouldSplitALine() {
        assertEquals(
                "a\fb c\u0085d\u000be f",
                read(MERGED.put("title", "\"a\\fb\\u2028c\\u0085d\\u000be\\u2029f\""))
                        .get()
                        .title());
    }

    @Test
    void detailsThatCannotBeReadAreUnexpected() {
        assertEquals(
                Either.left(new GhError.UnexpectedOutput("data.repository.pullRequest: expected an object, got null")),
                GhProcess.decoded(
                        "{ \"data\": { \"repository\": { \"nameWithOwner\": \"o/r\", \"pullRequest\": null } } }",
                        DetailsQuery.DECODER));
        assertEquals(
                Either.left(new GhError.UnexpectedOutput("data.repository.pullRequest.reviewThreads: missing")),
                read(MERGED.remove("reviewThreads")));
        assertEquals(
                Either.left(new GhError.UnexpectedOutput(
                        "data.repository.pullRequest.state: expected one of OPEN, CLOSED, MERGED, got \"DRAFT\"")),
                read(MERGED.put("state", "\"DRAFT\"")));
    }
}
