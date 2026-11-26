package net.vanfleteren.jazygit.features.github.failed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.CheckRun;
import net.vanfleteren.jazygit.core.gh.model.GhBranch;
import net.vanfleteren.jazygit.core.gh.model.PullRequest;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Review;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import org.junit.jupiter.api.Test;

/**
 * What failed on GitHub, handed to the actions on a failed build (ADR 161).
 */
class FailedBuildsTest {

    private static final BranchName FEAT = new BranchName("feat");
    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
    private static final Instant T = Instant.parse("2026-11-19T10:00:00Z");

    private static Build.Job job(String name, String conclusion) {
        return new Build.Job(name, conclusion, Option.of(T), conclusion.isEmpty() ? Option.none() : Option.of(T));
    }

    private static CheckRun run(long runId) {
        return new CheckRun(1, "CI", "FAILURE", Option.of(T), Option.of(T), runId, T, 1, "push");
    }

    @Test
    void theFailedJobsOfABuildAreHandedOverWithItsCommitAndRun() {
        Build build = new Build(
                "CI",
                "push",
                401,
                1,
                new Sha(COMMIT),
                "Fix",
                T,
                List.of(job("verify", "FAILURE"), job("lint", "SUCCESS"), job("docs", "TIMED_OUT")));

        FailedBuild failed = FailedBuilds.of(FEAT, build, Option.none());

        assertEquals(
                List.of(new FailedBuild.Job("verify", Option.none()), new FailedBuild.Job("docs", Option.none())),
                failed.jobs());
        assertEquals(Option.of(new Sha(COMMIT)), failed.commit());
        assertFalse(failed.running());
        assertEquals("gh run view 401 --log-failed", failed.logs());
    }

    @Test
    void thePullRequestOfTheBranchIsHandedOverAsTheChangeRequestTheForgeCallsIt() {
        PullRequest pullRequest =
                new PullRequest(new PrNumber(12), new GhBranch("feat"), false, Checks.FAILING, Review.NONE);
        Build build = new Build("CI", "push", 401, 1, new Sha(COMMIT), "Fix", T, List.of(job("verify", "FAILURE")));

        assertEquals(
                Option.of(new FailedBuild.ChangeRequest("pull request", new PrNumber(12))),
                FailedBuilds.of(FEAT, build, Option.of(pullRequest)).changeRequest());
        assertEquals(
                Option.of(new FailedBuild.ChangeRequest("pull request", new PrNumber(12))),
                FailedBuilds.of(FEAT, "o/r", List.empty(), Option.of(pullRequest))
                        .changeRequest());
        assertEquals(Option.none(), FailedBuilds.of(FEAT, build, Option.none()).changeRequest());
    }

    @Test
    void aBuildWithAJobStillRunningIsRunning() {
        Build build = new Build(
                "CI", "push", 401, 1, new Sha(COMMIT), "Fix", T, List.of(job("verify", "FAILURE"), job("e2e", "")));

        FailedBuild failed = FailedBuilds.of(FEAT, build, Option.none());

        assertTrue(failed.failed());
        assertTrue(failed.running());
    }

    @Test
    void theFailedChecksAreHandedOverAcrossRunsAndAStatusWithItsLink() {
        List<Check> checks = List.of(
                new Check("verify", Checks.FAILING, Option.of(run(401)), Option.of("https://github.com/o/r/job/1")),
                new Check("lint", Checks.PASSING, Option.of(run(401))),
                new Check("e2e", Checks.FAILING, Option.of(run(402))),
                new Check("ci/jenkins", Checks.FAILING, Option.none(), Option.of("https://jenkins.example/7")));

        FailedBuild failed = FailedBuilds.of(FEAT, "o/r", checks, Option.none());

        assertEquals(List.of("verify", "e2e", "ci/jenkins"), failed.jobs().map(FailedBuild.Job::name));
        assertEquals(Option.of("o/r"), failed.repository());
        assertEquals("""
                gh run view 401 --log-failed --repo o/r
                gh run view 402 --log-failed --repo o/r
                ci/jenkins: its logs are not on GitHub, but at https://jenkins.example/7""", failed.logs());
    }

    @Test
    void nothingFailedIsAFailedBuildWithoutJobs() {
        FailedBuild failed = FailedBuilds.of(
                FEAT, "o/r", List.of(new Check("verify", Checks.PENDING, Option.none())), Option.none());

        assertFalse(failed.failed());
        assertTrue(failed.running());
    }
}
