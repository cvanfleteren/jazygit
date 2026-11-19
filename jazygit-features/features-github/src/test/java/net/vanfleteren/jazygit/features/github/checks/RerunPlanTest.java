package net.vanfleteren.jazygit.features.github.checks;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.CheckRun;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Check;
import org.junit.jupiter.api.Test;

/**
 * What {@code r} in the checks popup runs again (ADR 135).
 */
class RerunPlanTest {

    /**
     * Check {@code name} of run {@code runId} of {@code workflow}, as {@code state}, ended as {@code conclusion}.
     */
    static Check check(String name, long runId, String workflow, Checks state, String conclusion) {
        return new Check(
                name,
                state,
                Option.of(new CheckRun(
                        name.hashCode(),
                        workflow,
                        conclusion,
                        Option.of(Instant.EPOCH),
                        state == Checks.PENDING ? Option.none() : Option.of(Instant.EPOCH.plusSeconds(60)),
                        runId,
                        Instant.EPOCH,
                        1,
                        "pull_request")));
    }

    @Test
    void eachFailedRunThatIsDoneIsRunAgainWithHowManyOfItsJobsFailedACancelledOneToo() {
        RerunPlan plan = RerunPlan.of(List.of(
                check("package", 1, "Build", Checks.PASSING, "SUCCESS"),
                check("verify", 1, "Build", Checks.FAILING, "FAILURE"),
                check("acceptance (1/2)", 1, "Build", Checks.FAILING, "TIMED_OUT"),
                check("e2e", 2, "E2E", Checks.FAILING, "CANCELLED"),
                check("lint", 3, "Markdown", Checks.PASSING, "SUCCESS")));

        assertEquals(
                List.of(
                        new RerunPlan.Run(1, "Build", "pull_request", 2),
                        new RerunPlan.Run(2, "E2E", "pull_request", 1)),
                plan.runs());
        assertEquals(List.empty(), plan.skipped());
    }

    @Test
    void aFailedRunThatStillRunsAndAFailedCheckOfNoWorkflowAreNotRunAgain() {
        RerunPlan plan = RerunPlan.of(List.of(
                check("verify", 1, "Build", Checks.FAILING, "FAILURE"),
                check("e2e", 2, "E2E", Checks.FAILING, "FAILURE"),
                check("e2e-slow", 2, "E2E", Checks.PENDING, ""),
                new Check("codecov/patch", Checks.FAILING)));

        assertEquals(List.of(new RerunPlan.Run(1, "Build", "pull_request", 1)), plan.runs());
        assertEquals(
                List.of(
                        new RerunPlan.Skipped("E2E", RerunPlan.Why.RUNNING),
                        new RerunPlan.Skipped("codecov/patch", RerunPlan.Why.NOT_ACTIONS)),
                plan.skipped());
    }

    @Test
    void nothingIsRunAgainWhenNoneFailedTheFailedOnesStillRunOrAreNotOfGitHubActions() {
        assertEquals(
                RerunPlan.Why.NONE_FAILED,
                RerunPlan.of(List.of(check("verify", 1, "Build", Checks.PASSING, "SUCCESS")))
                        .nothing());
        assertEquals(
                RerunPlan.Why.RUNNING,
                RerunPlan.of(List.of(
                                check("verify", 1, "Build", Checks.FAILING, "FAILURE"),
                                check("e2e", 1, "Build", Checks.PENDING, ""),
                                new Check("codecov/patch", Checks.FAILING)))
                        .nothing());
        assertEquals(
                RerunPlan.Why.NOT_ACTIONS,
                RerunPlan.of(List.of(new Check("codecov/patch", Checks.FAILING)))
                        .nothing());
    }
}
