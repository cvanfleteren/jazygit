package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GitHubAnswers.WAITING_SUITES;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.summary;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.MyPullRequests;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestList;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import org.junit.jupiter.api.Test;

/**
 * The pull requests that ask for my review and the ones I opened, in any repository, found by a search of issues.
 */
class MyPullRequestsQueryTest {

    private static Either<GhError.UnexpectedOutput, MyPullRequests> mine(
            int reviewCount, String reviewRequested, int authoredCount, String authored) {
        return GhProcess.decoded(
                """
                { "data": { "reviewRequested": { "issueCount": %d, "nodes": [ %s ] },
                  "authored": { "issueCount": %d, "nodes": [ %s ] } } }""".formatted(reviewCount, reviewRequested, authoredCount, authored), MyPullRequestsQuery.DECODER);
    }

    private static String hit(String summary) {
        return summary.replaceFirst("\\{", "{ \"__typename\": \"PullRequest\",");
    }

    @Test
    void theListsWithTheirPullRequestsAndHowManyMatch() {
        String fix = """
                { "__typename": "PullRequest", "number": 9, "title": "Fix the\\tlogin",
                  "url": "https://github.com/other/repo/pull/9", "updatedAt": "2026-10-16T21:39:59Z",
                  "isDraft": false, "isCrossRepository": false, "headRefName": "feature/login",
                  "author": { "login": "alice" }, "repository": { "nameWithOwner": "other/repo" },
                  "reviewDecision": "APPROVED", "stackEntry": null, "stack": null,
                  "commits": { "nodes": [ { "commit": { "statusCheckRollup": { "state": "SUCCESS" },
                    "checkSuites": { "nodes": [] } } } ] } }""";

        assertEquals(
                Either.right(new MyPullRequests(
                        new PullRequestList(
                                List.of(new PullRequestSummary(
                                        "other/repo",
                                        9,
                                        "Fix the\tlogin",
                                        "alice",
                                        "feature/login",
                                        false,
                                        false,
                                        Checks.PASSING,
                                        Review.APPROVED,
                                        Instant.parse("2026-10-16T21:39:59Z"),
                                        "https://github.com/other/repo/pull/9")),
                                52),
                        PullRequestList.EMPTY)),
                mine(52, fix, 0, ""));
    }

    @Test
    void aHitThatIsNoPullRequestIsLeftOut() {
        MyPullRequests mine = mine(
                        0, "", 2, "{ \"__typename\": \"Issue\" }, " + hit(summary(1, "null", WAITING_SUITES)))
                .get();

        assertEquals(List.of(1), mine.authored().pullRequests().map(PullRequestSummary::number));
        assertEquals(2, mine.authored().total());
    }

    @Test
    void aPullRequestFoundBySearchWhoseWorkflowWaitsToStartHasPendingChecks() {
        assertEquals(
                List.of(Checks.PENDING),
                mine(0, "", 1, hit(summary(1, "null", WAITING_SUITES)))
                        .get()
                        .authored()
                        .pullRequests()
                        .map(PullRequestSummary::checks));
    }

    @Test
    void aHitWithoutItsTypenameIsUnexpected() {
        assertTrue(mine(0, "", 1, "{}").isLeft());
        assertEquals(
                Either.left(new GhError.UnexpectedOutput("data.authored.nodes[0].__typename: missing")),
                mine(0, "", 1, "{}"));
    }

    @Test
    void theHitsAskForTheirTypename() {
        assertTrue(MyPullRequestsQuery.QUERY.contains("nodes { __typename ...summary }"), MyPullRequestsQuery.QUERY);
    }
}
