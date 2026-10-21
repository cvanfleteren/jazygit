package net.vanfleteren.jazygit.state;

import io.vavr.collection.HashMap;
import io.vavr.collection.HashSet;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Instant;
import lombok.With;
import net.vanfleteren.jazygit.git.model.BranchRef;

/**
 * What runs in the background, and when the remotes were last fetched from.
 *
 * @param refreshing periodic loads that have been started and not finished yet, so a tick does not queue them again
 * @param busy       the branches a git operation runs on right now, like a push or a delete, and that operation:
 *                   they show a spinner, and take no other operation until it is done
 * @param fetching   the background fetch from the remotes that is running, if any; the next one waits
 * @param lastFetch  when a remote was last fetched from, and the remotes that failed the last time
 * @param worktreeChangesRead when reading which worktrees have changes last finished, whatever came of it
 */
@With
public record Jobs(
        Set<Cmd.Load> refreshing,
        Map<BranchRef, Operation> busy,
        Option<Fetching> fetching,
        Freshness lastFetch,
        Option<Instant> worktreeChangesRead) {

    public static final Jobs IDLE =
            new Jobs(HashSet.empty(), HashMap.empty(), Option.none(), Freshness.NEVER, Option.none());

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
