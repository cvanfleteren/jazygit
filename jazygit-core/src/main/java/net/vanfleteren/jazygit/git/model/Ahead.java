package net.vanfleteren.jazygit.git.model;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * The commits of a branch that are not on its base, the branch it was made from.
 *
 * @param base      the branch compared with
 * @param commits   the newest commits only on the branch, newest first, at most a few hundred
 * @param more      whether there are more of them than {@code commits} holds
 * @param forkPoint the newest commit the branch and its base share, if they share one
 */
public record Ahead(BranchRef base, List<Commit> commits, boolean more, Option<Commit> forkPoint) {

    /**
     * Whether {@code sha} is on the base too, as far as can be told: when there are more commits only on the branch
     * than were read, none is.
     */
    public boolean shared(Sha sha) {
        return !more && !commits.exists(commit -> commit.sha().equals(sha));
    }

    /**
     * The commit {@code sha}, if it is one of the commits read or the fork point.
     */
    public Option<Commit> commit(Sha sha) {
        return commits.appendAll(forkPoint).find(commit -> commit.sha().equals(sha));
    }
}
