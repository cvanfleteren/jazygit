package net.vanfleteren.jazygit.features.forge.checks;

import static net.vanfleteren.jazygit.features.forge.checks.ForgeChecks.job;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.features.forge.checks.ForgeCheck.State;
import org.junit.jupiter.api.Test;

/**
 * Asking to run the failed jobs again names each run, and what is not run again, with why (ADR 135).
 */
class RerunDialogTest {

    @Test
    void askingNamesEachRunRunAgainAndWhatIsNotWithWhy() {
        ForgeCheck verify = job("verify", 1, "Build", State.FAILED);
        ForgeCheck e2e = job("e2e", 2, "E2E", State.FAILED);

        assertEquals(
                """
                Rerun the failed jobs of:
                  ✗ Build (push): 2 failed jobs
                  ✗ E2E (#2): 1 failed job

                Not rerun:
                  Markdown: still running
                  codecov/patch: not a GitHub Actions workflow""",
                RerunDialog.message(
                        new RerunPlan(
                                List.of(
                                        new RerunPlan.Run(1, "Build", "push", List.of(verify, verify)),
                                        new RerunPlan.Run(2, "E2E", "", List.of(e2e))),
                                List.of(
                                        new RerunPlan.Skipped("Markdown", RerunPlan.Why.RUNNING),
                                        new RerunPlan.Skipped("codecov/patch", RerunPlan.Why.NOT_ACTIONS))),
                        "GitHub"));
    }
}
