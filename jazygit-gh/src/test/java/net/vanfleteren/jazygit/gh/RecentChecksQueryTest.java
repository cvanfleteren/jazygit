package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GitHubAnswers.VERIFY;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.VERIFY_PASSED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.gh.model.RecentChecks;
import org.junit.jupiter.api.Test;

/**
 * The check runs that ran lately, on the pull requests merged last and on the default branch, to learn how long
 * checks take from.
 */
class RecentChecksQueryTest {

    private static final String OTHER_APP = """
            { "__typename": "CheckRun", "databaseId": 7, "name": "CodeQL", "status": "COMPLETED",
              "conclusion": "SUCCESS", "startedAt": null, "completedAt": null, "checkSuite": { "workflowRun": null } }""";

    private static Either<GhError.UnexpectedOutput, RecentChecks> recent(String merged, String defaultBranch) {
        return GhProcess.decoded("""
                { "data": { "repository": { "nameWithOwner": "cvanfleteren/jazygit",
                  "pullRequests": { "nodes": [ { "commits": { "nodes": [ { "commit": %s } ] } } ] },
                  "defaultBranchRef": %s } } }""".formatted(merged, defaultBranch), RecentChecksQuery.DECODER);
    }

    private static String rollup(String nodes) {
        return "{ \"statusCheckRollup\": { \"contexts\": { \"nodes\": [ " + nodes + " ] } } }";
    }

    @Test
    void theCheckRunsOfAWorkflowOnTheMergedPullRequestsAndTheDefaultBranch() {
        RecentChecks recent = recent(
                        rollup(VERIFY_PASSED + ", { \"__typename\": \"StatusContext\" }, " + OTHER_APP),
                        "{ \"target\": { \"__typename\": \"Commit\", \"history\": { \"nodes\": [ "
                                + rollup(VERIFY_PASSED) + ", { \"statusCheckRollup\": null } ] } } }")
                .get();

        assertEquals("cvanfleteren/jazygit", recent.repository());
        assertEquals(List.of(VERIFY, VERIFY), recent.checks());
    }

    @Test
    void aRepositoryWithoutADefaultBranchHasOnlyThoseOfItsPullRequests() {
        assertEquals(
                Either.right(new RecentChecks("cvanfleteren/jazygit", List.of(VERIFY))),
                recent(rollup(VERIFY_PASSED), "null"));
    }

    @Test
    void aCheckRunWithoutItsNameIsUnexpected() {
        assertEquals(
                Either.left(new GhError.UnexpectedOutput(
                        "data.repository.pullRequests.nodes[0].commits.nodes[0].commit.statusCheckRollup.contexts.nodes[0]"
                                + ".name: missing")),
                recent(
                        rollup("{ \"__typename\": \"CheckRun\", \"status\": \"COMPLETED\", \"conclusion\": null }"),
                        "null"));
    }
}
