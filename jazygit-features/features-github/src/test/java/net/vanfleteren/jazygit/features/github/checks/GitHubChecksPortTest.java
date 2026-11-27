package net.vanfleteren.jazygit.features.github.checks;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.CheckRun;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.features.forge.checks.ForgeCheck;
import net.vanfleteren.jazygit.features.forge.checks.RerunPlan;
import org.junit.jupiter.api.Test;

/**
 * The checks of GitHub as the checks flow reads them (ADR 209), and what {@code r} runs again of them (ADR 135).
 */
class GitHubChecksPortTest {

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
    void aCheckOfAWorkflowRunIsAJobOfItsRun() {
        Check verify = check("verify", 1, "Build", Checks.FAILING, "FAILURE");

        assertEquals(
                new ForgeCheck(
                        "verify",
                        ForgeCheck.State.FAILED,
                        Option.of(
                                new ForgeCheck.Job(1, Option.of((long) "verify".hashCode()), "Build", "pull_request")),
                        new GitHubChecksPort.Read(verify)),
                GitHubChecksPort.of(verify));
    }

    @Test
    void aSkippedCheckIsSkippedAQueuedOneHasNoJobYetAndACommitStatusIsNoJob() {
        Check queued = new Check(
                "e2e",
                Checks.PENDING,
                Option.of(
                        new CheckRun(0, "E2E", "", Option.none(), Option.none(), 2, Instant.EPOCH, 1, "pull_request")));

        assertEquals(
                ForgeCheck.State.SKIPPED,
                GitHubChecksPort.of(check("lint", 1, "Build", Checks.PASSING, "SKIPPED"))
                        .state());
        assertEquals(ForgeCheck.State.PENDING, GitHubChecksPort.of(queued).state());
        assertEquals(Option.none(), GitHubChecksPort.of(queued).job().flatMap(ForgeCheck.Job::id));
        assertEquals(
                Option.none(),
                GitHubChecksPort.of(new Check("codecov/patch", Checks.FAILING)).job());
    }

    @Test
    void theChecksOfGitHubAreReadBackAsTheyWere() {
        List<Check> checks = List.of(
                check("verify", 1, "Build", Checks.FAILING, "FAILURE"), new Check("codecov/patch", Checks.PASSING));

        assertEquals(checks, GitHubChecksPort.checks(checks.map(GitHubChecksPort::of)));
    }

    @Test
    void eachFailedWorkflowRunThatIsDoneIsRunAgainWithItsFailedJobsACancelledOneToo() {
        List<ForgeCheck> checks = List.of(
                        check("package", 1, "Build", Checks.PASSING, "SUCCESS"),
                        check("verify", 1, "Build", Checks.FAILING, "FAILURE"),
                        check("acceptance (1/2)", 1, "Build", Checks.FAILING, "TIMED_OUT"),
                        check("e2e", 2, "E2E", Checks.FAILING, "CANCELLED"),
                        check("lint", 3, "Markdown", Checks.PASSING, "SUCCESS"))
                .map(GitHubChecksPort::of);

        assertEquals(
                List.of(
                        new RerunPlan.Run(1, "Build", "pull_request", List.of(checks.get(1), checks.get(2))),
                        new RerunPlan.Run(2, "E2E", "pull_request", List.of(checks.get(3)))),
                RerunPlan.of(checks).runs());
    }
}
