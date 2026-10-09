package net.vanfleteren.jazygit.git;

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
    void commit(String summary, String description);

    /**
     * Amends the last commit with what is staged, keeping its message.
     *
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    void amendLastCommit();

    /**
     * Replaces the message of the last commit, leaving its content alone.
     *
     * @param description the extended message; may be blank
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    void reword(String summary, String description);
}
