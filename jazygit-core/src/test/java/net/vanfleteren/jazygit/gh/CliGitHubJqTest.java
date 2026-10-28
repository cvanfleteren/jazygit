package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GitHubAnswers.RAN_SUITES;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.VERIFY_PASSED;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.WAITING;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.WAITING_SUITES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Try;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import net.vanfleteren.jazygit.gh.model.BranchChecks;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import org.junit.jupiter.api.Test;

/**
 * The jq filters of {@link CliGitHub}, run by jq on what GitHub answers its queries, and what is parsed of what they
 * print (ADR 35). gh runs them with gojq, which these filters use no differences of. Without jq they are skipped,
 * but not on CI, where a missing jq fails them.
 */
class CliGitHubJqTest {

    /**
     * What jq prints of {@code json} through {@code filter}, as gh's {@code --jq} does: strings raw.
     */
    private static String jq(String filter, String json) {
        boolean available = Try.of(() -> new ProcessBuilder("jq", "--version")
                        .redirectErrorStream(true)
                        .start()
                        .waitFor(10, TimeUnit.SECONDS))
                .getOrElse(false);
        assumeTrue(available || System.getenv("CI") != null, "no jq");
        try {
            Process jq = new ProcessBuilder("jq", "-r", filter).start();
            jq.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
            jq.getOutputStream().close();
            String out = new String(jq.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String err = new String(jq.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, jq.waitFor(), err);
            return out;
        } catch (IOException | InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void aBranchWhoseWorkflowWaitsToStartIsPendingUnlessItsChecksFailed() {
        String json = """
                { "data": { "repository": { "nameWithOwner": "owner/repo", "refs": {
                  "pageInfo": { "hasNextPage": false, "endCursor": null }, "nodes": [
                    { "name": "main", "target": { "statusCheckRollup": null, %1$s } },
                    { "name": "broken", "target": { "statusCheckRollup": { "state": "FAILURE" }, %1$s } },
                    { "name": "feature", "target": { "statusCheckRollup": { "state": "SUCCESS" }, %2$s } },
                    { "name": "wip", "target": { "statusCheckRollup": null, "checkSuites": { "nodes": [] } } } ] } } } }
                """.formatted(WAITING_SUITES, RAN_SUITES);

        assertEquals(
                new BranchChecks(
                        "owner/repo",
                        HashMap.of(
                                "main",
                                Checks.PENDING,
                                "broken",
                                Checks.FAILING,
                                "feature",
                                Checks.PASSING,
                                "wip",
                                Checks.NONE)),
                CliGitHub.parseBranchChecks(jq(CliGitHub.BRANCHES_JQ, json)).get());
    }

    @Test
    void aWorkflowThatWaitsToStartIsAQueuedCheckOfItsBranchNextToItsCheckRuns() {
        String json = """
                { "data": { "repository": { "nameWithOwner": "owner/repo",
                  "b0": { "name": "main", "target": {
                    "statusCheckRollup": { "contexts": { "nodes": [ %s ] } }, %s } },
                  "b1": null } } }
                """.formatted(VERIFY_PASSED, WAITING_SUITES);

        List<Check> checks = CliGitHub.parseBranchCheckRuns(jq(CliGitHub.BRANCH_CHECK_RUNS_JQ, json))
                .get()
                .byBranch()
                .get("main")
                .get();

        assertEquals(List.of("verify", "Publish"), checks.map(Check::name));
        assertEquals(WAITING, checks.last());
        assertTrue(checks.last().queued());
    }

    @Test
    void aWorkflowThatWaitsToStartIsAQueuedCheckOfAPullRequest() {
        String json = """
                { "data": { "repository": { "nameWithOwner": "owner/repo", "pullRequest": {
                  "number": 42, "title": "Add", "author": { "login": "cvf" }, "state": "OPEN", "isDraft": false,
                  "headRefName": "feature", "baseRefName": "main", "headRefOid": "abc123",
                  "stackEntry": null, "stack": null,
                  "createdAt": "2026-10-26T22:00:00Z", "additions": 1, "deletions": 2, "changedFiles": 3,
                  "mergeable": "MERGEABLE", "reviewDecision": null, "comments": { "totalCount": 0 },
                  "labels": { "nodes": [] }, "latestReviews": { "nodes": [] }, "reviewRequests": { "nodes": [] },
                  "timelineItems": { "nodes": [] },
                  "commits": { "totalCount": 1, "nodes": [ { "commit": { "committedDate": "2026-10-26T22:00:00Z",
                    "statusCheckRollup": { "contexts": { "nodes": [ %s ] } }, %s } } ] },
                  "reviewThreads": { "totalCount": 0, "nodes": [] } } } } }
                """.formatted(VERIFY_PASSED, WAITING_SUITES);

        List<Check> checks =
                CliGitHub.parseDetails(jq(CliGitHub.DETAILS_JQ, json)).get().checks();

        assertEquals(List.of("verify", "Publish"), checks.map(Check::name));
        assertEquals(WAITING, checks.last());
    }

    @Test
    void aPullRequestWhoseWorkflowWaitsToStartHasPendingChecksInTheBranchesPane() {
        String json = """
                { "data": { "repository": { "pullRequests": {
                  "pageInfo": { "hasNextPage": false, "endCursor": null }, "nodes": [
                    { "number": 1, "headRefName": "waits", "isDraft": false, "isCrossRepository": false,
                      "reviewDecision": null, "stackEntry": null, "stack": null,
                      "commits": { "nodes": [ { "commit": {
                        "statusCheckRollup": { "contexts": { "nodes": [ %1$s ] } }, %2$s } } ] } },
                    { "number": 2, "headRefName": "ran", "isDraft": false, "isCrossRepository": false,
                      "reviewDecision": null, "stackEntry": null, "stack": null,
                      "commits": { "nodes": [ { "commit": {
                        "statusCheckRollup": { "contexts": { "nodes": [ %1$s ] } }, %3$s } } ] } } ] } } } }
                """.formatted(VERIFY_PASSED, WAITING_SUITES, RAN_SUITES);

        List<PullRequest> pullRequests = CliGitHub.parse(jq(CliGitHub.JQ, json)).get();

        assertEquals(List.of(Checks.PENDING, Checks.PASSING), pullRequests.map(PullRequest::checks));
    }

    @Test
    void aPullRequestWhoseWorkflowWaitsToStartHasPendingChecksInTheLists() {
        String json = """
                { "data": { "viewer": { "login": "cvf" }, "repository": { "nameWithOwner": "owner/repo",
                  "latest": { "nodes": [ { "number": 3 } ] },
                  "pullRequests": { "totalCount": 3, "nodes": [ %s, %s, %s ] } } } }
                """.formatted(
                        summary(1, "{ \"state\": \"SUCCESS\" }", WAITING_SUITES),
                        summary(2, "{ \"state\": \"FAILURE\" }", WAITING_SUITES),
                        summary(3, "null", RAN_SUITES));

        List<PullRequestSummary> listed = CliGitHub.parseRepositoryPullRequests(jq(CliGitHub.REPOSITORY_PRS_JQ, json))
                .get()
                .open()
                .pullRequests();

        assertEquals(List.of(Checks.PENDING, Checks.FAILING, Checks.NONE), listed.map(PullRequestSummary::checks));
    }

    @Test
    void aPullRequestFoundBySearchWhoseWorkflowWaitsToStartHasPendingChecks() {
        String json = """
                { "data": { "reviewRequested": { "issueCount": 0, "nodes": [] },
                  "authored": { "issueCount": 1, "nodes": [ %s ] } } }
                """.formatted(summary(1, "null", WAITING_SUITES));

        assertEquals(
                List.of(Checks.PENDING),
                CliGitHub.parseLists(jq(CliGitHub.MY_PRS_JQ, json))
                        .get()
                        .get("authored")
                        .get()
                        ._2
                        .pullRequests()
                        .map(PullRequestSummary::checks));
    }

    /**
     * Pull request {@code number} as the summary fragment reads it, its last commit with the rollup {@code rollup}
     * and the suites {@code suites}.
     */
    private static String summary(int number, String rollup, String suites) {
        return """
                { "number": %d, "title": "t", "url": "https://github.com/owner/repo/pull/%1$d",
                  "updatedAt": "2026-10-26T22:00:00Z", "isDraft": false, "isCrossRepository": false,
                  "headRefName": "b%1$d", "author": { "login": "cvf" }, "repository": { "nameWithOwner": "owner/repo" },
                  "reviewDecision": null, "stackEntry": null, "stack": null,
                  "commits": { "nodes": [ { "commit": { "statusCheckRollup": %s, %s } } ] } }""".formatted(number, rollup, suites);
    }
}
