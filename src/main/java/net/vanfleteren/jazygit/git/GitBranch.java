package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteRef;

/**
 * Listing, renaming and deleting branches, as {@code git branch} does.
 */
public interface GitBranch {

    /**
     * The local branches: the checked out one first, then the most recently committed to.
     */
    Either<ProcessError, List<Branch>> branches();

    /**
     * The remote-tracking branches, the most recently committed to first.
     */
    Either<ProcessError, List<RemoteRef>> remoteBranches();

    /**
     * Deletes the branch {@code name}, locally and/or {@code remote}, the branch it is pushed to. With both, the
     * remote one goes first, so a failure leaves the local branch in place. Returns the command lines it ran, or
     * why it failed together with the command lines run up to then.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteBranch(Branch.Name name, RemoteBranch remote,
                                                                         DeleteScope scope);

    /**
     * Renames the local branch {@code name} to {@code newName}; it keeps following the same upstream. Fails when
     * a branch is called {@code newName} already. Returns the command lines it ran, or why it failed together
     * with the command lines run up to then.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> renameBranch(Branch.Name name, Branch.Name newName);

    /**
     * Deletes {@code remote} on its remote. Returns the command lines it ran, or why it failed together with the
     * command lines run up to then.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> deleteRemoteBranch(RemoteBranch remote);
}
