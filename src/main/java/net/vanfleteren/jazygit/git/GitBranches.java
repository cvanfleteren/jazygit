package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.CheckoutError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.StashCheckoutError;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;

/**
 * Operations on branches. Each returns the command lines it ran, or why it failed together with the
 * command lines run up to then.
 */
public interface GitBranches {

    /**
     * Checks out the local branch {@code branch}. Fails with {@link GitError.LocalChanges} when
     * uncommitted changes would be overwritten.
     */
    Either<GitFailure<CheckoutError>, List<ExecutedCommand>> checkout(String branch);

    /**
     * Checks out the local branch {@code branch}, taking the uncommitted changes along: they are stashed
     * first and popped again afterwards. When the checkout itself fails, the changes are popped back
     * where they were; when popping them on {@code branch} fails, they stay in the stash, which is a
     * {@link GitError.StashNotRestored}.
     */
    Either<GitFailure<StashCheckoutError>, List<ExecutedCommand>> checkoutWithStash(String branch);

    /**
     * Creates the local branch {@code name} at {@code startPoint} and checks it out.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> createBranch(String name, String startPoint);

    /**
     * Creates the local branch {@code name} at the remote-tracking branch of {@code upstream}, following it, and
     * checks it out.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> createTrackingBranch(String name, RemoteBranch upstream);

    /**
     * Deletes the branch {@code name}, locally and/or {@code remote}, the branch it is pushed to. With both, the
     * remote one goes first, so a failure leaves the local branch in place.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteBranch(String name, RemoteBranch remote,
                                                                         DeleteScope scope);

    /**
     * Merges {@code branch} into the checked out branch, as {@code mode} says. A merge that stops on conflicts
     * is undone again, so the repository is left as it was.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> merge(String branch, MergeMode mode);

    /**
     * Rebases the checked out branch onto {@code onto}, bringing uncommitted changes along. When the rebase
     * fails, it is aborted again, so the repository is left as it was.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> rebase(String onto);

    /**
     * Pushes the local branch {@code branch} to {@code upstream}, the branch it tracks.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> push(String branch, RemoteBranch upstream, PushMode mode);

    /**
     * Pushes the local branch {@code branch} to {@code upstream}, which becomes its upstream once the push
     * succeeded.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> pushSettingUpstream(String branch, RemoteBranch upstream);

    /**
     * Pulls the checked out branch from its upstream, as git's {@code pull.rebase} and {@code pull.ff} say,
     * without asking for a merge message. A pull that stops on conflicts is undone again, so the repository is
     * left as it was.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> pull();

    /**
     * Fast-forwards the local branch {@code branch}, which is not checked out, to {@code upstream}, the branch it
     * tracks. Fails when that is not a fast-forward.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> fastForward(String branch, RemoteBranch upstream);
}
