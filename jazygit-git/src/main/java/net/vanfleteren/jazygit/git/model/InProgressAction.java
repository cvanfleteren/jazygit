package net.vanfleteren.jazygit.git.model;

/**
 * What can be done with an operation the repository is in the middle of, see {@link InProgress}.
 */
public enum InProgressAction {
    /**
     * Carries on with it, once its conflicts are resolved.
     */
    CONTINUE,
    /**
     * Undoes it, putting the repository back as it was before it started.
     */
    ABORT,
    /**
     * Leaves out the commit it stopped at, and carries on with the next; not for a merge.
     */
    SKIP
}
