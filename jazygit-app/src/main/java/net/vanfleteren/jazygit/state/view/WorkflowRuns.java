package net.vanfleteren.jazygit.state.view;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.Build;
import net.vanfleteren.jazygit.gh.model.WorkflowRunsPage;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Loadable;

/**
 * The runs of one workflow on every branch, read some at a time (ADR 131).
 *
 * @param builds  the runs read so far, as builds, the newest first
 * @param count   how many runs were read, of any event
 * @param after   where the runs still to be read start; none at the newest
 * @param reading whether more runs are being read
 * @param stopped why reading more failed, after some were read; none while it did not
 */
public record WorkflowRuns(
        Loadable<List<Build>> builds, int count, Option<String> after, boolean reading, Option<Failure> stopped) {

    /**
     * How many runs are read, at most.
     */
    public static final int RUNS = 100;

    /**
     * The runs of a workflow before any is read.
     */
    public static WorkflowRuns asked() {
        return new WorkflowRuns(Loadable.loading(), 0, Option.none(), true, Option.none());
    }

    /**
     * Whether these wait for the runs from {@code from} on.
     */
    public boolean awaits(Option<String> from) {
        return reading && after.equals(from);
    }

    /**
     * These, with the runs of {@code page} after those read; reading on while fewer than {@link #RUNS} were read and
     * older ones are left. None read means it is no workflow.
     */
    public WorkflowRuns read(Option<WorkflowRunsPage> page) {
        int now = count + page.map(WorkflowRunsPage::runs).getOrElse(0);
        Option<String> next = page.flatMap(WorkflowRunsPage::next);
        return new WorkflowRuns(
                new Loadable.Loaded<>(builds.getOrElse(List.empty())
                        .appendAll(page.map(WorkflowRunsPage::builds).getOrElse(List.empty()))),
                now,
                next,
                now < RUNS && next.isDefined(),
                Option.none());
    }

    /**
     * These, done reading as it failed for {@code failure}: what was read stays, stopped for it; with nothing read
     * yet, the runs failed.
     */
    public WorkflowRuns failed(Failure failure) {
        return new WorkflowRuns(
                builds.failed(failure), count, after, false, builds.toOption().map(ignored -> failure));
    }
}
