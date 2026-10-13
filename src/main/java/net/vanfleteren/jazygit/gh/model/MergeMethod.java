package net.vanfleteren.jazygit.gh.model;

/**
 * How a pull request is merged into its base branch.
 */
public enum MergeMethod {
    /** With a merge commit. */
    MERGE,
    /** Its commits squashed into one. */
    SQUASH,
    /** Its commits rebased onto the base branch. */
    REBASE
}
