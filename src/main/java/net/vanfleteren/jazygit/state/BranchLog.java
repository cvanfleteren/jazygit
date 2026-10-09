package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.git.model.Commit;

import java.util.List;
import java.util.Optional;

/**
 * The log of the branch highlighted in the branches pane.
 *
 * @param previous the last fully loaded log of another branch, kept while this one is loading
 *                 so the UI can keep showing it instead of flashing a placeholder
 */
public record BranchLog(String branch, Loadable<List<Commit>> commits, Optional<BranchLog> previous) {

    public BranchLog(String branch, Loadable<List<Commit>> commits) {
        this(branch, commits, Optional.empty());
    }

    /**
     * A log that is loading, falling back to the log that was shown before it.
     */
    public static BranchLog loading(String branch, Optional<BranchLog> shown) {
        Optional<BranchLog> previous = shown.flatMap(log ->
                log.commits() instanceof Loadable.Loaded<List<Commit>> ? Optional.of(log) : log.previous());
        return new BranchLog(branch, Loadable.loading(), previous);
    }

    /**
     * Like {@link Loadable#reload}, returns this instance when the commits are unchanged.
     */
    public BranchLog reload(List<Commit> commits) {
        Loadable<List<Commit>> reloaded = this.commits.reload(commits);
        return reloaded == this.commits ? this : new BranchLog(branch, reloaded);
    }
}
