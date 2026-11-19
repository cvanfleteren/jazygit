package net.vanfleteren.jazygit.features.github.checks;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.gh.model.CheckRun;
import net.vanfleteren.jazygit.core.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Check;

/**
 * What {@code r} in the checks popup runs again of the checks on the tip of a branch, and what it does not, and why
 * (ADR 135).
 *
 * @param runs    the failed workflow runs that are done, whose failed jobs are run again, the first listed first
 * @param skipped the failed checks that are not run again
 */
public record RerunPlan(List<Run> runs, List<Skipped> skipped) {

    /**
     * What there is to run again of {@code checks}: each workflow run of GitHub Actions with a check that failed, when
     * all its checks are done; a run with one that is not, or a failed check of no workflow run, is skipped. In the
     * order of the checks.
     */
    public static RerunPlan of(List<Check> checks) {
        List<Check> failed = checks.filter(check -> check.state() == Checks.FAILING);
        List<Long> failedRuns =
                failed.flatMap(check -> check.run().map(CheckRun::runId)).distinct();
        List<Run> runs = failedRuns
                .filter(runId -> ofRun(checks, runId).forAll(Check::done))
                .map(runId -> run(checks, runId));
        List<Skipped> running = failedRuns
                .filter(runId -> !ofRun(checks, runId).forAll(Check::done))
                .map(runId -> new Skipped(run(checks, runId).workflow(), Why.RUNNING));
        List<Skipped> notActions =
                failed.filter(check -> check.run().isEmpty()).map(check -> new Skipped(check.name(), Why.NOT_ACTIONS));
        return new RerunPlan(runs, running.appendAll(notActions));
    }

    /**
     * Why nothing is run again, when nothing is: as long as some failed run is not done, that it is; else that the
     * failed checks are not of GitHub Actions; else that none failed.
     */
    public Why nothing() {
        return skipped.exists(skip -> skip.why() == Why.RUNNING)
                ? Why.RUNNING
                : skipped.isEmpty() ? Why.NONE_FAILED : Why.NOT_ACTIONS;
    }

    private static List<Check> ofRun(List<Check> checks, long runId) {
        return checks.filter(check -> check.run().exists(run -> run.runId() == runId));
    }

    private static Run run(List<Check> checks, long runId) {
        List<Check> of = ofRun(checks, runId);
        // Only called for a run some check is of.
        CheckRun first = of.flatMap(Check::run).head();
        return new Run(runId, first.workflow(), first.event(), of.count(check -> check.state() == Checks.FAILING));
    }

    /**
     * A workflow run whose failed jobs are run again.
     *
     * @param runId      its id
     * @param workflow   the name of its workflow
     * @param event      what started it, like {@code push}
     * @param failedJobs how many of its jobs failed
     */
    public record Run(long runId, String workflow, String event, int failedJobs) {}

    /**
     * A failed check, or the workflow run of failed checks, that is not run again.
     *
     * @param name the check's name, or the workflow's
     */
    public record Skipped(String name, Why why) {}

    /**
     * Why a failed check, or all of them, are not run again.
     */
    public enum Why {
        /** No check failed. */
        NONE_FAILED,
        /** Its workflow run still runs, which GitHub does not run again before it is done. */
        RUNNING,
        /** It is of another app than GitHub Actions, or a commit status. */
        NOT_ACTIONS
    }
}
