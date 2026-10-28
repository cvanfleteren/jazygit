package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GitHubAnswers.RAN_SUITES;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.WAITING;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.WAITING_SUITES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import org.junit.jupiter.api.Test;

/**
 * The workflow runs of a commit that wait to start, as {@link SuiteFragment} finds them in its check suites
 * (ADR 35).
 */
class SuiteFragmentTest {

    private static Either<GhError.UnexpectedOutput, List<Check>> waiting(String suites) {
        return GhProcess.decoded("{" + suites + "}", SuiteFragment.WAITING);
    }

    @Test
    void aWorkflowRunThatWaitsToStartIsAQueuedCheckWithoutAnIdOfItsOwn() {
        List<Check> waiting = waiting(WAITING_SUITES).get();

        assertEquals(List.of(WAITING), waiting);
        assertTrue(waiting.head().queued());
    }

    @Test
    void aWorkflowThatRanOrWasCompletedOrOfAnotherAppDoesNotWait() {
        assertEquals(Either.right(List.empty()), waiting(RAN_SUITES));
    }

    @Test
    void withoutSuitesNothingWaits() {
        assertEquals(Either.right(List.empty()), waiting("\"checkSuites\": null"));
        assertEquals(Either.right(List.empty()), waiting("\"checkSuites\": { \"nodes\": [] }"));
    }

    @Test
    void aWaitingRunWhoseStartCannotBeReadStillWaitsButIsNotTimed() {
        assertEquals(Either.right(List.of(new Check("Publish", Checks.PENDING))), waiting("""
                        "checkSuites": { "nodes": [
                          { "status": "QUEUED", "checkRuns": { "totalCount": 0 }, "workflowRun": {
                            "databaseId": 404, "createdAt": null, "runAttempt": 1, "event": "push",
                            "workflow": { "name": "Publish" } } } ] }"""));
    }

    @Test
    void aSuiteWithoutItsStatusIsUnexpected() {
        assertEquals(
                Either.left(new GhError.UnexpectedOutput("checkSuites.nodes[0].status: missing")),
                waiting(
                        "\"checkSuites\": { \"nodes\": [ { \"checkRuns\": { \"totalCount\": 0 }, \"workflowRun\": null } ] }"));
    }
}
