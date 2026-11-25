package net.vanfleteren.jazygit.features.forgejo.checks;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.ForgejoCheck;
import net.vanfleteren.jazygit.core.forgejo.ForgejoCheck.ActionsJob;

/**
 * What {@code r} in the checks popup runs again of the checks on the tip of a branch, and what it does not, and why
 * (ADR 135, 195): as GitHub's, the failed jobs of each run of Forgejo Actions that is done, each with the jobs that
 * need it.
 *
 * @param runs    the runs that are done with a failed job, whose failed jobs are run again, the first listed first
 * @param skipped the failed checks that are not run again
 */
public record RerunPlan(List<Run> runs, List<Skipped> skipped) {

    /**
     * What there is to run again of {@code checks}: the failed jobs of each run of Forgejo Actions with one, when all
     * its jobs are done; a run with one that is not, or a failed status of another CI, is skipped. In the order of the
     * checks.
     */
    public static RerunPlan of(List<ForgejoCheck> checks) {
        List<ForgejoCheck> failed = checks.filter(check -> check.state() == CommitStatus.State.FAILED);
        List<Long> failedRuns =
                failed.flatMap(check -> check.job().map(ActionsJob::run)).distinct();
        List<Run> runs = failedRuns
                .filter(run -> ofRun(checks, run).forAll(ForgejoCheck::done))
                .map(run -> run(checks, run));
        List<Skipped> running = failedRuns
                .filter(run -> !ofRun(checks, run).forAll(ForgejoCheck::done))
                .map(run -> new Skipped(run(checks, run).workflow(), Why.RUNNING));
        List<Skipped> notActions =
                failed.filter(check -> check.job().isEmpty()).map(check -> new Skipped(check.name(), Why.NOT_ACTIONS));
        return new RerunPlan(runs, running.appendAll(notActions));
    }

    /**
     * Why nothing is run again, when nothing is: as long as some failed run is not done, that it is; else that the
     * failed checks are not of Forgejo Actions; else that none failed.
     */
    public Why nothing() {
        return skipped.exists(skip -> skip.why() == Why.RUNNING)
                ? Why.RUNNING
                : skipped.isEmpty() ? Why.NONE_FAILED : Why.NOT_ACTIONS;
    }

    private static List<ForgejoCheck> ofRun(List<ForgejoCheck> checks, long run) {
        return checks.filter(check -> check.job().exists(job -> job.run() == run));
    }

    private static Run run(List<ForgejoCheck> checks, long run) {
        List<ForgejoCheck> of = ofRun(checks, run);
        // Only called for a run some check is of.
        Context first = Context.of(of.head().name());
        return new Run(
                run,
                first.workflow(),
                first.event(),
                of.filter(check -> check.state() == CommitStatus.State.FAILED).flatMap(ForgejoCheck::job));
    }

    /**
     * A run of Forgejo Actions whose failed jobs are run again.
     *
     * @param run      its number in the repository
     * @param workflow the name of its workflow
     * @param event    what started it, like {@code push}; empty when its checks do not say
     * @param failed   its failed jobs
     */
    public record Run(long run, String workflow, String event, List<ActionsJob> failed) {}

    /**
     * A failed check, or the run of failed checks, that is not run again.
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
        /** Its run still runs: its jobs are run again once it is done. */
        RUNNING,
        /** It is a status of another CI than Forgejo Actions. */
        NOT_ACTIONS
    }

    /**
     * What Forgejo Actions names a job's status, {@code <workflow> / <job> (<event>)}.
     */
    private record Context(String workflow, String event) {

        static Context of(String name) {
            int slash = name.indexOf(" / ");
            int open = name.lastIndexOf(" (");
            return new Context(
                    slash > 0 ? name.substring(0, slash) : name,
                    open > 0 && name.endsWith(")") ? name.substring(open + 2, name.length() - 1) : "");
        }
    }
}
