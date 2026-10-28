package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GitHubAnswers.VERIFY_PASSED;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.WAITING;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.WAITING_SUITES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.vavr.collection.List;
import io.vavr.control.Try;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
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
}
