package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.model.DiscardPlan;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Operations on the index (ADR 69).
 */
public interface Index {

    /**
     * Stages the changes of {@code paths}.
     */
    Either<GitFailure, List<ExecutedCommand>> stage(List<String> paths);

    /**
     * Removes the newly added {@code paths} from the index; they stay in the working tree, untracked.
     */
    Either<GitFailure, List<ExecutedCommand>> unstageNew(List<String> paths);

    /**
     * Throws changes away as described by {@code plan}. This cannot be undone.
     */
    Either<GitFailure, List<ExecutedCommand>> discard(DiscardPlan plan);

    /**
     * Resets the index entries of {@code paths} to HEAD.
     */
    Either<GitFailure, List<ExecutedCommand>> unstage(List<String> paths);
}
