package net.vanfleteren.jazygit.git;

/**
 * Operations on branches.
 */
public interface GitBranches {

    /**
     * Checks out the local branch {@code branch}.
     *
     * @throws IllegalStateException if the checkout fails, with git's explanation as message
     */
    void checkout(String branch);

    /**
     * Creates the local branch {@code name} at {@code startPoint} and checks it out.
     *
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    void createBranch(String name, String startPoint);

    /**
     * Deletes the branch {@code name}, locally and/or on its remote. With both, the remote one goes
     * first, so a failure leaves the local branch in place.
     *
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    void deleteBranch(String name, boolean local, boolean remote);
}
