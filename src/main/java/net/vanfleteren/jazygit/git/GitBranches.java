package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.CheckoutError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.StashCheckoutError;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.PushMode;

/**
 * Operations on branches. Each returns the command lines it ran, or why it failed together with the
 * command lines run up to then.
 */
public interface GitBranches {

    /**
     * Checks out the local branch {@code branch}. Fails with {@link GitError.LocalChanges} when
     * uncommitted changes would be overwritten.
     */
    Either<GitFailure<CheckoutError>, List<String>> checkout(String branch);

    /**
     * Checks out the local branch {@code branch}, taking the uncommitted changes along: they are stashed
     * first and popped again afterwards. When the checkout itself fails, the changes are popped back
     * where they were; when popping them on {@code branch} fails, they stay in the stash, which is a
     * {@link GitError.StashNotRestored}.
     */
    Either<GitFailure<StashCheckoutError>, List<String>> checkoutWithStash(String branch);

    /**
     * Creates the local branch {@code name} at {@code startPoint} and checks it out.
     */
    Either<GitFailure<ProcessError>, List<String>> createBranch(String name, String startPoint);

    /**
     * Deletes the branch {@code name}, locally and/or on its remote. With both, the remote one goes
     * first, so a failure leaves the local branch in place.
     */
    Either<GitFailure<ProcessError>, List<String>> deleteBranch(String name, DeleteScope scope);

    /**
     * Rebases the checked out branch onto {@code onto}, bringing uncommitted changes along. When the rebase
     * fails, it is aborted again, so the repository is left as it was.
     */
    Either<GitFailure<ProcessError>, List<String>> rebase(String onto);

    /**
     * Pushes the local branch {@code branch} to its remote, setting that as the upstream when the
     * branch has none yet.
     */
    Either<GitFailure<ProcessError>, List<String>> push(String branch, PushMode mode);
}
