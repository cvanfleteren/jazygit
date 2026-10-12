package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.git.model.Sha;

import java.util.List;

/**
 * Operations that create or rewrite commits.
 */
public interface GitCommits {

    /**
     * Commits what is staged.
     *
     * @param description the extended message; may be blank
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    List<String> commit(String summary, String description);

    /**
     * Amends the last commit with what is staged, keeping its message.
     *
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    List<String> amendLastCommit();

    /**
     * Replaces the message of the commit {@code sha}, leaving its content alone. Other than for the last commit,
     * this rebases the commits after it, uncommitted changes being stashed meanwhile; the rebase is aborted
     * when it fails.
     *
     * @param sha the full object id of the commit
     *
     * @param description the extended message; may be blank
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    List<String> reword(Sha sha, String summary, String description);

    /**
     * Squashes the commit {@code sha} into its parent, keeping both messages. This rebases the commits after
     * it, uncommitted changes being stashed meanwhile; the rebase is aborted when it fails.
     *
     * @param sha the full object id of a commit that has exactly one parent
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    List<String> squash(Sha sha);
}
