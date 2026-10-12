package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.DiscardError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.DiscardPlan;

/**
 * Operations on the index. Each returns the command lines it ran, or why it failed together with the
 * command lines run up to then.
 */
public interface GitIndex {

    /**
     * Stages the changes of {@code paths}.
     */
    Either<GitFailure<ProcessError>, List<String>> stage(List<String> paths);

    /**
     * Removes the newly added {@code paths} from the index; they stay in the working tree, untracked.
     */
    Either<GitFailure<ProcessError>, List<String>> unstageNew(List<String> paths);

    /**
     * Throws changes away as described by {@code plan}. This cannot be undone.
     */
    Either<GitFailure<DiscardError>, List<String>> discard(DiscardPlan plan);

    /**
     * Resets the index entries of {@code paths} to HEAD.
     */
    Either<GitFailure<ProcessError>, List<String>> unstage(List<String> paths);
}
