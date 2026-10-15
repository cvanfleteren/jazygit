package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.Branch;

/**
 * Rebasing the checked out branch, as {@code git rebase} does.
 */
public interface GitRebase {

    /**
     * Rebases the checked out branch onto {@code onto}, bringing uncommitted changes along. When the rebase
     * fails, it is aborted again, so the repository is left as it was. Returns the command lines it ran, or why
     * it failed together with the command lines run up to then.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> rebase(Branch.Name onto);
}
