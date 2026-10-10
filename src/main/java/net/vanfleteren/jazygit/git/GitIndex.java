package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.git.model.DiscardPlan;

import java.util.List;

/**
 * Operations on the index.
 */
public interface GitIndex {

    /**
     * Stages the changes of {@code paths}.
     *
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    void stage(List<String> paths);

    /**
     * Removes the newly added {@code paths} from the index; they stay in the working tree, untracked.
     *
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    void unstageNew(List<String> paths);

    /**
     * Throws changes away as described by {@code plan}. This cannot be undone.
     *
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    void discard(DiscardPlan plan);

    /**
     * Resets the index entries of {@code paths} to HEAD.
     *
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    void unstage(List<String> paths);
}
