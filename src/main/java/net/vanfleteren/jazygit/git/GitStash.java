package net.vanfleteren.jazygit.git;

import java.util.List;

/**
 * Operations on stashes.
 */
public interface GitStash {

    /**
     * Stashes the changes, staged and unstaged, and the untracked files, under the name {@code message}.
     *
     * @return the command lines that were run
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    List<String> stash(String message);

    /**
     * Applies the stash {@code ref} (e.g. {@code stash@{1}}) to the working tree and drops it. When that
     * conflicts, the stash is kept.
     *
     * @return the command lines that were run
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    List<String> pop(String ref);

    /**
     * Deletes the stash {@code ref} (e.g. {@code stash@{1}}) without applying it.
     *
     * @return the command lines that were run
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    List<String> drop(String ref);
}
