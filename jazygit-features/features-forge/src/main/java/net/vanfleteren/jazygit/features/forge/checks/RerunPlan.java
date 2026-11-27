package net.vanfleteren.jazygit.features.forge.checks;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Failure;

/**
 * What {@code r} in the checks popup runs again of the checks on the tip of a branch, and what it does not, and why
 * (ADR 135, 209): the failed jobs of each run of the forge's Actions that is done.
 *
 * @param runs    the runs that are done with a failed job, whose failed jobs are run again, the first listed first
 * @param skipped the failed checks that are not run again
 */
public record RerunPlan(List<Run> runs, List<Skipped> skipped) {

    /**
     * What there is to run again of {@code checks}: the failed jobs of each run of the forge's Actions with one, when
     * all its jobs are done; a run with one that is not, or a failed check of another CI, is skipped. In the order of
     * the checks.
     */
    public static RerunPlan of(List<ForgeCheck> checks) {
        List<ForgeCheck> failed = checks.filter(ForgeCheck::failed);
        List<Long> failedRuns =
                failed.flatMap(check -> check.job().map(ForgeCheck.Job::run)).distinct();
        List<Run> runs = failedRuns
                .filter(run -> ofRun(checks, run).forAll(ForgeCheck::done))
                .map(run -> run(checks, run));
        List<Skipped> running = failedRuns
                .filter(run -> !ofRun(checks, run).forAll(ForgeCheck::done))
                .map(run -> new Skipped(run(checks, run).workflow(), Why.RUNNING));
        List<Skipped> notActions =
                failed.filter(check -> check.job().isEmpty()).map(check -> new Skipped(check.name(), Why.NOT_ACTIONS));
        return new RerunPlan(runs, running.appendAll(notActions));
    }

    /**
     * Why nothing is run again, when nothing is: as long as some failed run is not done, that it is; else that the
     * failed checks are not of the forge's Actions; else that none failed.
     */
    public Why nothing() {
        return skipped.exists(skip -> skip.why() == Why.RUNNING)
                ? Why.RUNNING
                : skipped.isEmpty() ? Why.NONE_FAILED : Why.NOT_ACTIONS;
    }

    private static List<ForgeCheck> ofRun(List<ForgeCheck> checks, long run) {
        return checks.filter(check -> check.job().exists(job -> job.run() == run));
    }

    private static Run run(List<ForgeCheck> checks, long run) {
        List<ForgeCheck> of = ofRun(checks, run);
        // Only called for a run some check is of.
        ForgeCheck.Job first = of.flatMap(ForgeCheck::job).head();
        return new Run(run, first.workflow(), first.event(), of.filter(ForgeCheck::failed));
    }

    /**
     * A run of the forge's Actions whose failed jobs are run again.
     *
     * @param run      its number in the repository
     * @param workflow the name of its workflow
     * @param event    what started it, like {@code push}; empty when its checks do not say
     * @param failed   its failed jobs
     */
    public record Run(long run, String workflow, String event, List<ForgeCheck> failed) {}

    /**
     * A failed check, or the run of failed checks, that is not run again.
     *
     * @param name the check's name, or the workflow's
     */
    public record Skipped(String name, Why why) {}

    /**
     * How running the failed jobs of {@code run} again went: refused, with why, or not.
     */
    public record Outcome(Run run, Option<Failure> refused) {}

    /**
     * Why a failed check, or all of them, are not run again.
     */
    public enum Why {
        /** No check failed. */
        NONE_FAILED,
        /** Its run still runs: the forge runs its jobs again once it is done. */
        RUNNING,
        /** It is a check of another CI than the forge's Actions, or a commit status. */
        NOT_ACTIONS
    }
}
