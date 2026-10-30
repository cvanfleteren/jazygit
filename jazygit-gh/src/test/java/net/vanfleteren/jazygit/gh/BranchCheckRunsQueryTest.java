package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GitHubAnswers.VERIFY;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.VERIFY_PASSED;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.WAITING;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.WAITING_SUITES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.BranchCheckRuns;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.timing.CheckSample;
import org.junit.jupiter.api.Test;

/**
 * The checks on the tips of branches, each read in an alias of its own.
 */
class BranchCheckRunsQueryTest {

    private static Either<GhError.UnexpectedOutput, BranchCheckRuns> runs(int count, String aliases) {
        return GhProcess.decoded(
                "{ \"data\": { \"repository\": { \"nameWithOwner\": \"cvanfleteren/jazygit\", " + aliases + " } } }",
                BranchCheckRunsQuery.decoder(count));
    }

    @Test
    void theChecksOfEachBranchByBranchWithItsWorkflowRunsThatWaitToStart() {
        BranchCheckRuns runs =
                runs(3, """
                        "b0": { "name": "main", "target": { "__typename": "Commit",
                          "statusCheckRollup": { "contexts": { "nodes": [ %s ] } }, %s } },
                        "b1": { "name": "feat/x", "target": { "__typename": "Commit",
                          "statusCheckRollup": { "contexts": { "nodes": [
                            { "__typename": "StatusContext", "context": "ci/status", "state": "SUCCESS" } ] } },
                          "checkSuites": { "nodes": [] } } },
                        "b2": null""".formatted(VERIFY_PASSED, WAITING_SUITES)).get();

        assertEquals("cvanfleteren/jazygit", runs.repository());
        assertEquals(
                HashMap.of("main", List.of(VERIFY, WAITING), "feat/x", List.of(new Check("ci/status", Checks.PASSING))),
                runs.byBranch());
    }

    @Test
    void aWorkflowRunThatWaitsToStartIsAQueuedCheckThatIsNeverLearnedFrom() {
        Check waiting = runs(1, """
                        "b0": { "name": "main", "target": { "__typename": "Commit", "statusCheckRollup": null, %s } }""".formatted(WAITING_SUITES))
                .get()
                .byBranch()
                .get("main")
                .get()
                .head();

        assertEquals(WAITING, waiting);
        assertTrue(waiting.queued());
        assertEquals(Option.none(), CheckSample.of(waiting));
    }

    @Test
    void aBranchWithoutChecksIsNotThere() {
        assertEquals(Either.right(new BranchCheckRuns("cvanfleteren/jazygit", HashMap.empty())), runs(1, """
                        "b0": { "name": "main", "target": { "__typename": "Commit", "statusCheckRollup": null,
                          "checkSuites": { "nodes": [] } } }"""));
    }

    @Test
    void anAliasThatIsMissingIsUnexpected() {
        assertEquals(Either.left(new GhError.UnexpectedOutput("data.repository.b1: missing")), runs(2, "\"b0\": null"));
    }

    @Test
    void theChecksOfEachBranchAreReadInAnAliasOfTheirOwn() {
        String query = BranchCheckRunsQuery.query(2);

        assertTrue(query.contains("$name: String!, $b0: String!, $b1: String!)"), query);
        assertTrue(query.contains("b0: ref(qualifiedName: $b0)"), query);
        assertTrue(query.contains("b1: ref(qualifiedName: $b1)"), query);
        assertFalse(query.contains("$b2"), query);
    }

    @Test
    void theBranchesAreAskedForByTheirFullRefInTheVariablesOfTheirAlias() {
        assertTrue(BranchCheckRunsQuery.command(List.of("feat/x", "main"))
                .executed()
                .commandLine()
                .containsSlice(List.of("-f", "b0=refs/heads/feat/x", "-f", "b1=refs/heads/main")));
    }
}
