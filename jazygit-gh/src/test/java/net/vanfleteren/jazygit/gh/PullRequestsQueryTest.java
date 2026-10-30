package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GitHubAnswers.NOT_STACKED;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.RAN_SUITES;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.STACKED;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.VERIFY_PASSED;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.WAITING_SUITES;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.State;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import org.junit.jupiter.api.Test;

/**
 * The open pull requests of the branches, from all pages GitHub answers.
 */
class PullRequestsQueryTest {

    private static final String NO_SUITES = "\"checkSuites\": { \"nodes\": [] }";

    private static Either<GhError.UnexpectedOutput, List<PullRequest>> pullRequests(String... pages) {
        return GhProcess.decoded(
                List.of(pages).map(nodes -> """
                                { "data": { "repository": { "pullRequests": {
                                  "pageInfo": { "hasNextPage": false, "endCursor": null }, "nodes": [ %s ] } } } }""".formatted(nodes)).mkString("[", ", ", "]"), PullRequestsQuery.DECODER);
    }

    private static String pullRequest(
            int number, String head, boolean draft, String review, String contexts, String suites, String stack) {
        return """
                { "number": %d, "headRefName": "%s", "isDraft": %b, "isCrossRepository": false,
                  "reviewDecision": %s, %s,
                  "commits": { "nodes": [ { "commit": {
                    "statusCheckRollup": %s, %s } } ] } }""".formatted(number, head, draft, review, stack, contexts, suites);
    }

    private static String contexts(String... states) {
        return List.of(states)
                .map(state -> state.equals("PENDING")
                        ? "{ \"__typename\": \"CheckRun\", \"status\": \"IN_PROGRESS\", \"conclusion\": null }"
                        : "{ \"__typename\": \"CheckRun\", \"status\": \"COMPLETED\", \"conclusion\": \"" + state
                                + "\" }")
                .mkString("{ \"contexts\": { \"nodes\": [ ", ", ", " ] } }");
    }

    @Test
    void thePullRequestsOverAllPagesWithTheirChecksAndReview() {
        assertEquals(
                Either.right(List.of(
                        new PullRequest(9, "feature/login", false, Checks.PASSING, Review.APPROVED),
                        new PullRequest(7, "wip", true, Checks.NONE, Review.NONE),
                        new PullRequest(5, "fix/npe", false, Checks.PENDING, Review.CHANGES_REQUESTED))),
                pullRequests(
                        pullRequest(
                                        9,
                                        "feature/login",
                                        false,
                                        "\"APPROVED\"",
                                        contexts("SUCCESS", "SKIPPED"),
                                        NO_SUITES,
                                        NOT_STACKED)
                                + ", " + pullRequest(7, "wip", true, "null", "null", NO_SUITES, NOT_STACKED),
                        pullRequest(
                                5,
                                "fix/npe",
                                false,
                                "\"CHANGES_REQUESTED\"",
                                contexts("SUCCESS", "PENDING"),
                                NO_SUITES,
                                NOT_STACKED)));
    }

    @Test
    void aPullRequestWhoseWorkflowWaitsToStartHasPendingChecksInTheBranchesPane() {
        String verify = "{ \"contexts\": { \"nodes\": [ " + VERIFY_PASSED + " ] } }";

        assertEquals(
                List.of(Checks.PENDING, Checks.PASSING),
                pullRequests(pullRequest(1, "waits", false, "null", verify, WAITING_SUITES, NOT_STACKED) + ", "
                                + pullRequest(2, "ran", false, "null", verify, RAN_SUITES, NOT_STACKED))
                        .get()
                        .map(PullRequest::checks));
    }

    @Test
    void aStackedPullRequestSaysWhereItSitsInItsStack() {
        assertEquals(
                Option.of(new PullRequestStack(
                        96,
                        2,
                        2,
                        List.of(
                                new PullRequestStack.Entry(1, 94, State.MERGED),
                                new PullRequestStack.Entry(2, 95, State.OPEN)))),
                pullRequests(pullRequest(95, "fix-abort-amend", false, "null", "null", NO_SUITES, STACKED))
                        .get()
                        .head()
                        .stack());
    }

    @Test
    void aPullRequestFromAForkIsLeftOut() {
        assertEquals(
                Either.right(List.empty()),
                pullRequests(pullRequest(9, "patch-1", false, "null", "null", NO_SUITES, NOT_STACKED)
                        .replace("\"isCrossRepository\": false", "\"isCrossRepository\": true")));
    }

    @Test
    void noPullRequestsIsAnEmptyList() {
        assertEquals(Either.right(List.empty()), pullRequests(""));
    }

    @Test
    void aPullRequestThatCannotBeReadIsUnexpected() {
        assertEquals(
                Either.left(new GhError.UnexpectedOutput(
                        "[0].data.repository.pullRequests.nodes[0].number: expected a number, got a string")),
                pullRequests(pullRequest(9, "x", false, "null", "null", NO_SUITES, NOT_STACKED)
                        .replace("\"number\": 9", "\"number\": \"nine\"")));
    }
}
