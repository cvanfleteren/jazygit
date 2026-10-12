package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.RewordError;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * Operations that create or rewrite commits. Each returns the command lines it ran, or why it failed
 * together with the command lines run up to then.
 */
public interface GitCommits {

    /**
     * Commits what is staged.
     */
    Either<GitFailure<ProcessError>, List<String>> commit(CommitMessage message);

    /**
     * Amends the last commit with what is staged, keeping its message.
     */
    Either<GitFailure<ProcessError>, List<String>> amendLastCommit();

    /**
     * Replaces the message of the commit {@code sha}, leaving its content alone. Other than for the last commit,
     * this rebases the commits after it, uncommitted changes being stashed meanwhile; the rebase is aborted
     * when it fails.
     *
     * @param sha the full object id of the commit
     */
    Either<GitFailure<RewordError>, List<String>> reword(Sha sha, CommitMessage message);

    /**
     * Squashes the commit {@code sha} into its parent, keeping both messages. This rebases the commits after
     * it, uncommitted changes being stashed meanwhile; the rebase is aborted when it fails.
     *
     * @param sha the full object id of a commit that has exactly one parent
     */
    Either<GitFailure<ProcessError>, List<String>> squash(Sha sha);
}
