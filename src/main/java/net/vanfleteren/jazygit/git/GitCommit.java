package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.RewordError;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.SquashMode;

/**
 * Creating and rewriting commits, as {@code git commit} does, and through an interactive rebase for older
 * commits. Each returns the command lines it ran, or why it failed together with the command lines run up to
 * then.
 */
public interface GitCommit {

    /**
     * Commits what is staged.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> commit(CommitMessage message);

    /**
     * Amends the last commit with what is staged, keeping its message.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> amendLastCommit();

    /**
     * Replaces the message of the commit {@code sha}, leaving its content alone. Other than for the last commit,
     * this rebases the commits after it, uncommitted changes being stashed meanwhile; the rebase is aborted
     * when it fails.
     *
     * @param sha the full object id of the commit
     */
    Either<GitFailure<RewordError>, List<ExecutedCommand>> reword(Sha sha, CommitMessage message);

    /**
     * Folds the commit {@code sha} into its parent, keeping the messages {@code mode} says. This rebases the
     * commits after it, uncommitted changes being stashed meanwhile; the rebase is aborted when it fails.
     *
     * @param sha the full object id of a commit that has exactly one parent
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> squash(Sha sha, SquashMode mode);
}
