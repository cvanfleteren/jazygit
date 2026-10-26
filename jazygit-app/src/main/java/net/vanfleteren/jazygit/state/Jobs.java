package net.vanfleteren.jazygit.state;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Instant;
import lombok.With;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.MetadataStamp;

/**
 * What runs in the background, and when the remotes were last fetched from.
 *
 * @param refreshing periodic loads that have been started and not finished yet, so a tick does not queue them again
 * @param failed     the reads of the log of HEAD, and of what is ahead on its base, that failed, whatever was kept of
 *                   them: a tick reads them again, as what is kept may be that of another HEAD
 * @param busy       the branches a git operation runs on right now, like a push or a delete, and that operation:
 *                   they show a spinner, and take no other operation until it is done
 * @param fetching   the background fetch from the remotes that is running, if any; the next one waits
 * @param lastFetch  when a remote was last fetched from, and the remotes that failed the last time
 * @param worktreeChangesRead when reading which worktrees have changes last finished, whatever came of it
 * @param readAt     for each load that {@link Cmd.CheckMetadata} guards, what the files it reads looked like when it
 *                   was last started; it is not started again until they change. A load that fails is forgotten,
 *                   so the next tick tries again
 * @param ghExecutableEntered the gh executable the user entered last, to be checked before it is used, if any:
 *                            none inside for the one on the PATH. Checks can finish in another order than they
 *                            were asked for; only the check of this one decides
 * @param ticks      how many ticks there were, which tells when a read that failed is tried again
 * @param retries    the reads that failed the last time they ran, with how often they failed in a row and the tick
 *                   they last failed on: a tick only tries one again once it waited longer the more it failed
 * @param running    every command that runs, by the id it was given when it was asked for, until it is done (ADR 33):
 *                   queued, waiting for its guard or running, the user waits for it all the same
 * @param nextId     the id the next command that is asked for gets
 */
@With
public record Jobs(
        Set<Cmd.Load> refreshing,
        Set<Cmd.Load> failed,
        Map<BranchRef, Operation> busy,
        Option<Fetching> fetching,
        Freshness lastFetch,
        Option<Instant> worktreeChangesRead,
        Map<Cmd.Load, MetadataStamp> readAt,
        Option<Option<String>> ghExecutableEntered,
        long ticks,
        Map<Cmd.Load, Retry> retries,
        Map<Long, Running> running,
        long nextId) {

    public static final Jobs IDLE = new Jobs(
            HashSet.empty(),
            HashSet.empty(),
            HashMap.empty(),
            Option.none(),
            Freshness.NEVER,
            Option.none(),
            HashMap.empty(),
            Option.none(),
            0,
            HashMap.empty(),
            HashMap.empty(),
            0);

    /**
     * Caps how long a tick waits before trying a read that failed again at {@code 2^6} = 64 ticks, under a minute at
     * the default interval.
     */
    private static final int MAX_BACKOFF_SHIFT = 6;

    /**
     * These jobs one tick further.
     */
    public Jobs ticked() {
        return withTicks(ticks + 1);
    }

    /**
     * These jobs knowing that {@code load} ran, and whether it {@code failed}: one more failure in a row, or none.
     */
    public Jobs withRead(Cmd.Load load, boolean failed) {
        return withRetries(
                failed
                        ? retries.put(
                                load,
                                new Retry(retries.get(load).map(Retry::failures).getOrElse(0) + 1, ticks))
                        : retries.remove(load));
    }

    /**
     * These jobs keeping only the failures of {@code loads}, the reads that are still to be tried again: those of a
     * preview that is no longer shown, say, are forgotten.
     */
    public Jobs retainingRetries(Set<Cmd.Load> loads) {
        return withRetries(retries.filterKeys(loads::contains));
    }

    /**
     * Whether a tick tries {@code load} again: once it waited one tick after failing the first time, then twice as
     * long after each failure in a row, up to {@code 2^MAX_BACKOFF_SHIFT} ticks. One that never failed always is.
     */
    public boolean retryDue(Cmd.Load load) {
        return retries.get(load)
                .forAll(retry -> ticks - retry.tick() >= 1L << Math.min(retry.failures() - 1, MAX_BACKOFF_SHIFT));
    }

    /**
     * These jobs knowing that {@code load} failed, if it is one that is read again for that: the log of HEAD, and
     * what is ahead on its base.
     */
    public Jobs withFailedRead(Cmd.Load load) {
        return retried(load) ? withFailed(failed.add(load)) : this;
    }

    /**
     * These jobs with {@code load} not failed anymore, as it was read, and any like it that failed before.
     */
    public Jobs withoutFailedRead(Cmd.Load load) {
        return retried(load) ? withFailed(failed.reject(other -> sameRead(other, load))) : this;
    }

    /**
     * Whether the log of HEAD failed to be read.
     */
    public boolean commitsFailed() {
        return failed.exists(load -> load instanceof Cmd.LoadCommits);
    }

    /**
     * Whether what is ahead on the base of HEAD failed to be read.
     */
    public boolean headAheadFailed() {
        return failed.exists(load -> load instanceof Cmd.LoadAhead);
    }

    private static boolean retried(Cmd.Load load) {
        return load instanceof Cmd.LoadCommits
                || load instanceof Cmd.LoadAhead ahead && ahead.branch().isEmpty();
    }

    private static boolean sameRead(Cmd.Load a, Cmd.Load b) {
        return a.getClass().equals(b.getClass());
    }

    /**
     * These jobs with {@code operation} running on {@code branch}.
     */
    public Jobs withBusyBranch(BranchRef branch, Operation operation) {
        return withBusy(busy.put(branch, operation));
    }

    /**
     * These jobs with the operation on {@code branch} done.
     */
    public Jobs withoutBusyBranch(BranchRef branch) {
        return withBusy(busy.remove(branch));
    }

    /**
     * These jobs with {@code cmds} asked for at {@code now}: each runs, with the next id. The ids are given in the
     * order of {@code cmds}.
     */
    public Jobs withStarted(List<Cmd> cmds, Instant now) {
        return withRunning(running.merge(HashMap.ofEntries(cmds.zipWithIndex()
                        .map(cmd -> Tuple.of(nextId + cmd._2(), new Running(cmd._1(), now))))))
                .withNextId(nextId + cmds.size());
    }

    /**
     * These jobs with the command {@code id} done.
     */
    public Jobs withDone(long id) {
        return withRunning(running.remove(id));
    }

    /**
     * A command that runs, asked for at {@code started}.
     */
    public record Running(Cmd cmd, Instant started) {}

    /**
     * A read that failed {@code failures} times in a row, the last time on tick {@code tick}.
     */
    public record Retry(int failures, long tick) {}
}
