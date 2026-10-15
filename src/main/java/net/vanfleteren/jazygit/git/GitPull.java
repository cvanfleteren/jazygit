package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;

/**
 * Pulling the checked out branch, as {@code git pull} does.
 */
public interface GitPull {

    /**
     * Pulls the checked out branch from its upstream, as git's {@code pull.rebase} and {@code pull.ff} say,
     * without asking for a merge message. A pull that stops on conflicts is undone again, so the repository is
     * left as it was. Returns the command lines it ran, or why it failed together with the command lines run up
     * to then.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> pull();
}
