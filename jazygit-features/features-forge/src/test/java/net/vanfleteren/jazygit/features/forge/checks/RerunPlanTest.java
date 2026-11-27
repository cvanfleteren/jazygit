package net.vanfleteren.jazygit.features.forge.checks;

import static net.vanfleteren.jazygit.features.forge.checks.ForgeChecks.job;
import static net.vanfleteren.jazygit.features.forge.checks.ForgeChecks.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.features.forge.checks.ForgeCheck.State;
import org.junit.jupiter.api.Test;

/**
 * What {@code r} in the checks popup runs again (ADR 135, 209).
 */
class RerunPlanTest {

    @Test
    void eachFailedRunThatIsDoneIsRunAgainWithItsFailedJobs() {
        ForgeCheck verify = job("verify", 1, "Build", State.FAILED);
        ForgeCheck acceptance = job("acceptance (1/2)", 1, "Build", State.FAILED);
        ForgeCheck e2e = job("e2e", 2, "E2E", State.FAILED);

        RerunPlan plan = RerunPlan.of(List.of(
                job("package", 1, "Build", State.PASSED),
                verify,
                acceptance,
                e2e,
                job("lint", 3, "Markdown", State.PASSED)));

        assertEquals(
                List.of(
                        new RerunPlan.Run(1, "Build", "push", List.of(verify, acceptance)),
                        new RerunPlan.Run(2, "E2E", "push", List.of(e2e))),
                plan.runs());
        assertEquals(List.empty(), plan.skipped());
    }

    @Test
    void aFailedRunThatStillRunsAndAFailedCheckOfAnotherCiAreNotRunAgain() {
        ForgeCheck verify = job("verify", 1, "Build", State.FAILED);

        RerunPlan plan = RerunPlan.of(List.of(
                verify,
                job("e2e", 2, "E2E", State.FAILED),
                job("e2e-slow", 2, "E2E", State.PENDING),
                status("codecov/patch", State.FAILED)));

        assertEquals(List.of(new RerunPlan.Run(1, "Build", "push", List.of(verify))), plan.runs());
        assertEquals(
                List.of(
                        new RerunPlan.Skipped("E2E", RerunPlan.Why.RUNNING),
                        new RerunPlan.Skipped("codecov/patch", RerunPlan.Why.NOT_ACTIONS)),
                plan.skipped());
    }

    @Test
    void nothingIsRunAgainWhenNoneFailedTheFailedOnesStillRunOrAreNotOfTheForgesActions() {
        assertEquals(
                RerunPlan.Why.NONE_FAILED,
                RerunPlan.of(List.of(job("verify", 1, "Build", State.PASSED), job("lint", 1, "Build", State.SKIPPED)))
                        .nothing());
        assertEquals(
                RerunPlan.Why.RUNNING,
                RerunPlan.of(List.of(
                                job("verify", 1, "Build", State.FAILED),
                                job("e2e", 1, "Build", State.PENDING),
                                status("codecov/patch", State.FAILED)))
                        .nothing());
        assertEquals(
                RerunPlan.Why.NOT_ACTIONS,
                RerunPlan.of(List.of(status("codecov/patch", State.FAILED))).nothing());
    }
}
