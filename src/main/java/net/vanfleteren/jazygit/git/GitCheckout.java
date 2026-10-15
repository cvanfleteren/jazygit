package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.CheckoutError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.StashCheckoutError;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RemoteBranch;

/**
 * Checking out branches, as {@code git checkout} does. Each returns the command lines it ran, or why it failed
 * together with the command lines run up to then.
 */
public interface GitCheckout {

    /**
     * Checks out the local branch {@code branch}. Fails with {@link GitError.LocalChanges} when
     * uncommitted changes would be overwritten.
     */
    Either<GitFailure<CheckoutError>, List<ExecutedCommand>> checkout(Branch.Name branch);

    /**
     * Checks out the local branch {@code branch}, taking the uncommitted changes along: they are stashed
     * first and popped again afterwards. When the checkout itself fails, the changes are popped back
     * where they were; when popping them on {@code branch} fails, they stay in the stash, which is a
     * {@link GitError.StashNotRestored}.
     */
    Either<GitFailure<StashCheckoutError>, List<ExecutedCommand>> checkoutWithStash(Branch.Name branch);

    /**
     * Creates the local branch {@code name} at {@code startPoint} and checks it out.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> checkoutNewBranch(Branch.Name name, String startPoint);

    /**
     * Creates the local branch {@code name} at the remote-tracking branch of {@code upstream}, following it, and
     * checks it out.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> checkoutNewTrackingBranch(Branch.Name name, RemoteBranch upstream);
}
