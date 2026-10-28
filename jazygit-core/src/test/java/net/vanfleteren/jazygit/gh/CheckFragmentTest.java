package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GitHubAnswers.VERIFY;
import static net.vanfleteren.jazygit.gh.GitHubAnswers.VERIFY_PASSED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import org.junit.jupiter.api.Test;

/**
 * A check on a commit, as {@link CheckFragment} decodes it.
 */
class CheckFragmentTest {

    private static Either<GhError.UnexpectedOutput, Check> check(String json) {
        return GhProcess.decoded(json, CheckFragment.CHECK);
    }

    private static Either<GhError.UnexpectedOutput, String> state(String json) {
        return GhProcess.decoded(json, CheckFragment.STATE);
    }

    @Test
    void oneFailedCheckFailsThemAllElseOneRunningKeepsThemPending() {
        assertEquals(Checks.NONE, CheckFragment.checks(List.empty()));
        assertEquals(Checks.PASSING, CheckFragment.checks(List.of("SUCCESS", "NEUTRAL", "SKIPPED")));
        assertEquals(Checks.PENDING, CheckFragment.checks(List.of("SUCCESS", "PENDING")));
        assertEquals(Checks.FAILING, CheckFragment.checks(List.of("PENDING", "FAILURE")));
        assertEquals(Checks.FAILING, CheckFragment.checks(List.of("SUCCESS", "ERROR")));
    }

    @Test
    void aCheckRunThatIsNotCompletedIsPendingElseItsConclusion() {
        assertEquals(
                Either.right("PENDING"),
                state("{\"__typename\": \"CheckRun\", \"status\": \"IN_PROGRESS\", \"conclusion\": null}"));
        assertEquals(
                Either.right("FAILURE"),
                state("{\"__typename\": \"CheckRun\", \"status\": \"COMPLETED\", \"conclusion\": \"FAILURE\"}"));
        assertEquals(
                Either.right(""),
                state("{\"__typename\": \"CheckRun\", \"status\": \"COMPLETED\", \"conclusion\": null}"));
    }

    @Test
    void aCommitStatusGivesItsState() {
        assertEquals(Either.right("ERROR"), state("{\"__typename\": \"StatusContext\", \"state\": \"ERROR\"}"));
        assertEquals(Either.right(""), state("{\"__typename\": \"StatusContext\", \"state\": null}"));
    }

    @Test
    void aCheckRunOfAWorkflowIsTimed() {
        assertEquals(Either.right(VERIFY), check(VERIFY_PASSED));
    }

    @Test
    void aCheckRunOfAnotherAppIsNotTimed() {
        assertEquals(Either.right(new Check("CodeQL", Checks.PENDING)), check("""
                        { "__typename": "CheckRun", "databaseId": 7, "name": "CodeQL", "status": "QUEUED",
                          "conclusion": null, "startedAt": null, "completedAt": null,
                          "checkSuite": { "workflowRun": null } }"""));
    }

    @Test
    void aCheckRunWhoseTimingCannotBeReadIsNotTimedButStillCounts() {
        assertEquals(Either.right(new Check("verify", Checks.PENDING)), check("""
                        { "__typename": "CheckRun", "databaseId": 122, "name": "verify", "status": "IN_PROGRESS",
                          "conclusion": null, "startedAt": "2026-10-26T22:00:02Z", "completedAt": null,
                          "checkSuite": { "workflowRun": { "databaseId": 401, "createdAt": "nope",
                            "runAttempt": 1, "event": "push", "workflow": { "name": "Build" } } } }"""));
    }

    @Test
    void aCheckRunWhoseIdIsNullIsNotTimedButStillCounts() {
        assertEquals(Either.right(new Check("verify", Checks.PENDING)), check("""
                        { "__typename": "CheckRun", "databaseId": null, "name": "verify", "status": "IN_PROGRESS",
                          "conclusion": null, "startedAt": "2026-10-26T22:00:02Z", "completedAt": null,
                          "checkSuite": { "workflowRun": { "databaseId": 401, "createdAt": "2026-10-26T22:00:00Z",
                            "runAttempt": 1, "event": "push", "workflow": { "name": "Build" } } } }"""));
    }

    @Test
    void aCheckRunWithoutWhenItRanIsUnexpected() {
        assertEquals(Either.left(new GhError.UnexpectedOutput("startedAt: missing")), check("""
                        { "__typename": "CheckRun", "databaseId": 122, "name": "verify", "status": "IN_PROGRESS",
                          "conclusion": null, "completedAt": null, "checkSuite": { "workflowRun": null } }"""));
        assertEquals(
                Either.left(new GhError.UnexpectedOutput("checkSuite.workflowRun.createdAt: missing")), check("""
                        { "__typename": "CheckRun", "databaseId": 122, "name": "verify", "status": "IN_PROGRESS",
                          "conclusion": null, "startedAt": null, "completedAt": null,
                          "checkSuite": { "workflowRun": { "databaseId": 401,
                            "runAttempt": 1, "event": "push", "workflow": { "name": "Build" } } } }"""));
    }

    @Test
    void aCommitStatusIsACheckByItsContext() {
        assertEquals(
                Either.right(new Check("ci/travis", Checks.FAILING)),
                check("{\"__typename\": \"StatusContext\", \"context\": \"ci/travis\", \"state\": \"FAILURE\"}"));
    }

    @Test
    void aCheckOfATypeNotAskedForHasNoNameOrState() {
        assertEquals(Either.right(new Check("", Checks.NONE, Option.none())), check("{\"__typename\": \"Other\"}"));
    }

    @Test
    void aCheckWithoutItsTypenameIsUnexpected() {
        assertEquals(Either.left(new GhError.UnexpectedOutput("__typename: missing")), check("{\"name\": \"verify\"}"));
    }

    @Test
    void aCheckRunWithoutItsStatusIsUnexpected() {
        assertEquals(
                Either.left(new GhError.UnexpectedOutput("status: missing")),
                check("{\"__typename\": \"CheckRun\", \"name\": \"verify\"}"));
    }
}
