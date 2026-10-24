package net.vanfleteren.jazygit.state;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
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
 * @param busy      the branches a git operation runs on right now, like a push or a delete, and that operation:
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
        Option<Option<String>> ghExecutableEntered) {

    public static final Jobs IDLE = new Jobs(
            HashSet.empty(),
            HashSet.empty(),
            HashMap.empty(),
            Option.none(),
            Freshness.NEVER,
            Option.none(),
            HashMap.empty(),
            Option.none());

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
}
