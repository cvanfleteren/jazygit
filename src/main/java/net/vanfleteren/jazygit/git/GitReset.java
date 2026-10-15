package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.ResetMode;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * Moving the checked out branch, as {@code git reset} does.
 */
public interface GitReset {

    /**
     * Moves the checked out branch to the commit {@code sha}, doing with the changes {@code mode} says. Returns
     * the command lines it ran, or why it failed together with the command lines run up to then.
     *
     * @param sha the full object id of the commit
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> reset(Sha sha, ResetMode mode);
}
