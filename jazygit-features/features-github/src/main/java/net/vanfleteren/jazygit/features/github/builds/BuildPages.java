package net.vanfleteren.jazygit.features.github.builds;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.Build;
import net.vanfleteren.jazygit.core.gh.model.BuildsPage;
import net.vanfleteren.jazygit.core.gh.model.WorkflowRunsPage;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Loadable;

/**
 * The runs of one workflow on every branch, or the builds on the commits of a branch, read some at a time (ADR 131,
 * 212).
 *
 * @param builds the runs read so far, as builds, the newest first
 * @param count  how many runs were read, of any event
 * @param limit  how many runs are read before it stops, until more are asked for
 * @param paging whether more runs are being read, are left to read from where, or why reading them stopped
 */
public record BuildPages(Loadable<List<Build>> builds, int count, int limit, Paging paging) {

    /**
     * How many runs are read at first, and how many more each time more are asked for (ADR 131).
     */
    public static final int RUNS = 100;

    /**
     * The runs of a workflow before any is read.
     */
    public static BuildPages asked() {
        return new BuildPages(Loadable.loading(), 0, RUNS, new Paging.Reading(Option.none()));
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
    public BuildPages read(Option<WorkflowRunsPage> page) {
        return read(
                page.map(WorkflowRunsPage::builds).getOrElse(List.empty()),
                page.map(WorkflowRunsPage::runs).getOrElse(0),
                page.flatMap(WorkflowRunsPage::next));
    }

    /**
     * These, with the builds on the commits of {@code page} after those read, as {@link #read(Option)}: of a branch,
     * where {@code count} counts commits (ADR 212).
     */
    public BuildPages readCommits(Option<BuildsPage> page) {
        return read(
                page.map(BuildsPage::builds).getOrElse(List.empty()),
                page.map(BuildsPage::commits).getOrElse(0),
                page.flatMap(BuildsPage::next));
    }

    /**
     * The builds of a branch or a workflow before any is read, which stop once {@code limit} runs or commits are.
     */
    public static BuildPages asked(int limit) {
        return new BuildPages(Loadable.loading(), 0, limit, new Paging.Reading(Option.none()));
    }

    /**
     * These, with {@code newest}, the newest page read again: each build in it takes the place of the one of the same
     * run read before, and the older ones read stay, so that a look again at what runs keeps what {@code m} read (ADR
     * 212). Read before any was, it is the first page.
     */
    public BuildPages refreshed(List<Build> newest) {
        List<Build> before = builds.getOrElse(List.empty());
        return new BuildPages(
                new Loadable.Loaded<>(
                        newest.appendAll(before.filter(old -> !newest.exists(build -> build.runId() == old.runId())))),
                count,
                limit,
                paging);
    }

    private BuildPages read(List<Build> page, int read, Option<String> next) {
        int now = count + read;
        return new BuildPages(
                new Loadable.Loaded<>(builds.getOrElse(List.empty()).appendAll(page)),
                now,
                limit,
                Paging.after(now, limit, next));
    }

    /**
     * These, done reading as it failed for {@code failure}: what was read stays, stopped for it; with nothing read
     * yet, the runs failed.
     */
    public BuildPages failed(Failure failure) {
        return new BuildPages(
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
    public Option<BuildPages> more() {
        return Option.when(
                hasMore(),
                () -> new BuildPages(
                        builds.isFailed() ? Loadable.loading() : builds, count, count + RUNS, paging.more()));
    }
}
