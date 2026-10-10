package net.vanfleteren.jazygit.git;

import java.util.List;

/**
 * Operations on branches.
 */
public interface GitBranches {

    /**
     * Checks out the local branch {@code branch}.
     *
     * @throws LocalChangesException if git refuses because uncommitted changes would be overwritten
     * @throws IllegalStateException if the checkout fails, with git's explanation as message
     */
    List<String> checkout(String branch);

    /**
     * Checks out the local branch {@code branch}, taking the uncommitted changes along: they are stashed
     * first and popped again afterwards. When the checkout itself fails, the changes are popped back
     * where they were.
     *
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    List<String> checkoutWithStash(String branch);

    /**
     * Creates the local branch {@code name} at {@code startPoint} and checks it out.
     *
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    List<String> createBranch(String name, String startPoint);

    /**
     * Deletes the branch {@code name}, locally and/or on its remote. With both, the remote one goes
     * first, so a failure leaves the local branch in place.
     *
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    List<String> deleteBranch(String name, boolean local, boolean remote);

    /**
     * Pushes the local branch {@code branch} to its remote, setting that as the upstream when the
     * branch has none yet.
     *
     * @param forceWithLease overwrite the remote branch, but only if it is still where this
     *                       repository last saw it ({@code --force-with-lease})
     * @throws IllegalStateException if git fails, with git's explanation as message
     */
    List<String> push(String branch, boolean forceWithLease);
}
