package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;

/**
 * Operations on stashes. Each returns the command lines it ran, or why it failed together with the
 * command lines run up to then.
 */
public interface GitStash {

    /**
     * Stashes the changes, staged and unstaged, and the untracked files, under the name {@code message}.
     */
    Either<GitFailure<ProcessError>, List<String>> stash(String message);

    /**
     * Applies the stash {@code ref} (e.g. {@code stash@{1}}) to the working tree and drops it. When that
     * conflicts, the stash is kept.
     */
    Either<GitFailure<ProcessError>, List<String>> pop(String ref);

    /**
     * Deletes the stash {@code ref} (e.g. {@code stash@{1}}) without applying it.
     */
    Either<GitFailure<ProcessError>, List<String>> drop(String ref);
}
