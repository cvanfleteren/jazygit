package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.StashError;
import net.vanfleteren.jazygit.git.model.Stash;

/**
 * Operations on stashes. Each returns the command lines it ran, or why it failed together with the
 * command lines run up to then.
 */
public interface GitStash {

    /**
     * Stashes the changes, staged and unstaged, and the untracked files, under the name {@code message}.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> stash(Option<String> message);

    /**
     * Applies {@code stash} to the working tree and drops it. When that conflicts, the stash is kept.
     */
    Either<GitFailure<StashError>, List<ExecutedCommand>> pop(Stash stash);

    /**
     * Deletes {@code stash} without applying it.
     */
    Either<GitFailure<StashError>, List<ExecutedCommand>> drop(Stash stash);
}
