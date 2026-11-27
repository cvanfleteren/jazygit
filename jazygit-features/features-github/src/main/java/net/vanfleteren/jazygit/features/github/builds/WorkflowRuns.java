package net.vanfleteren.jazygit.features.github.builds;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.WorkflowRunsPage;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Loadable;

/**
 * The runs of one workflow on every branch, read some at a time (ADR 131).
 *
 * @param builds the runs read so far, as builds, the newest first
 * @param count  how many runs were read, of any event
 * @param limit  how many runs are read before it stops, until more are asked for
 * @param paging whether more runs are being read, are left to read from where, or why reading them stopped
 */
public record WorkflowRuns(Loadable<List<Build>> builds, int count, int limit, Paging paging) {

    /**
     * How many runs are read at first, and how many more each time more are asked for (ADR 131).
     */
    public static final int RUNS = 100;

    /**
     * The runs of a workflow before any is read.
     */
    public static WorkflowRuns asked() {
        return new WorkflowRuns(Loadable.loading(), 0, RUNS, new Paging.Reading(Option.none()));
    }

    /**
     * Whether these wait for the runs from {@code from} on.
     */
    public boolean awaits(Option<String> from) {
        return paging.awaits(from);
    }

    /**
     * Whether more runs are being read.
     */
    public boolean reading() {
        return paging.reading();
    }

    /**
     * These, with the runs of {@code page} after those read; reading on while fewer than {@code limit} were read and
     * older ones are left. None read means it is no workflow.
     */
    public WorkflowRuns read(Option<WorkflowRunsPage> page) {
        int now = count + page.map(WorkflowRunsPage::runs).getOrElse(0);
        return new WorkflowRuns(
                new Loadable.Loaded<>(builds.getOrElse(List.empty())
                        .appendAll(page.map(WorkflowRunsPage::builds).getOrElse(List.empty()))),
                now,
                limit,
                Paging.after(now, limit, page.flatMap(WorkflowRunsPage::next)));
    }

    /**
     * These, done reading as it failed for {@code failure}: what was read stays, stopped for it; with nothing read
     * yet, the runs failed.
     */
    public WorkflowRuns failed(Failure failure) {
        return new WorkflowRuns(
                builds.failed(failure),
                count,
                limit,
                paging.failed(failure, builds.toOption().isDefined()));
    }

    /**
     * Whether more runs can be asked for: older ones are left, or reading the first ones failed, and none are being
     * read (ADR 131).
     */
    public boolean hasMore() {
        return !paging.reading() && (paging.from().isDefined() || builds.isFailed());
    }

    /**
     * Whether reading the runs failed: the first ones, or more after some were read.
     */
    public boolean hasFailed() {
        return builds.isFailed() || paging instanceof Paging.Stopped;
    }

    /**
     * These, reading {@link #RUNS} more runs from where those read end, or again from where reading them failed, from
     * the newest when it failed at the first; none when no older ones are left, or they are being read (ADR 131).
     */
    public Option<WorkflowRuns> more() {
        return Option.when(
                hasMore(),
                () -> new WorkflowRuns(
                        builds.isFailed() ? Loadable.loading() : builds, count, count + RUNS, paging.more()));
    }
}
