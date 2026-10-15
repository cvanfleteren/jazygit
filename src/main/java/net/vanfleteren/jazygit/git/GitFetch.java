package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RemoteBranch;

/**
 * Fetching from the remotes, as {@code git fetch} does. Each returns the command lines it ran, or why it failed
 * together with the command lines run up to then.
 */
public interface GitFetch {

    /**
     * Fetches from {@code remote}. It never asks for credentials or passphrases: what would need them fails.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> fetch(String remote);

    /**
     * Fast-forwards the local branch {@code branch}, which is not checked out, to {@code upstream}, the branch it
     * tracks. Fails when that is not a fast-forward.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> fastForward(Branch.Name branch, RemoteBranch upstream);
}
