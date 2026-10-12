package net.vanfleteren.jazygit.git.model;

/**
 * How a branch is merged into the checked out one.
 */
public enum MergeMode {
    /**
     * Fast-forward when the checked out branch has nothing of its own, else a merge commit
     * ({@code git merge --ff}).
     */
    FAST_FORWARD,
    /**
     * Always a merge commit ({@code git merge --no-ff}).
     */
    MERGE_COMMIT,
    /**
     * The changes of the branch, staged but not committed ({@code git merge --squash}).
     */
    SQUASH,
    /**
     * The changes of the branch as one new commit, with git's squash message.
     */
    SQUASH_AND_COMMIT
}
