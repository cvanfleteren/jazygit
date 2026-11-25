package net.vanfleteren.jazygit.core.git.files;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.ConflictTools;
import net.vanfleteren.jazygit.core.git.model.DiscardPlan;
import net.vanfleteren.jazygit.core.git.model.MarkedFile;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;

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

    /**
     * Resolves the conflict in {@code path} by keeping {@code version}'s side of it (ADR 198): its version, checked
     * out and staged; or, when that side deleted the file, the file removed.
     */
    Either<GitFailure, List<ExecutedCommand>> keep(String path, Conflict conflict, Conflict.Version version);

    /**
     * Puts back the conflict in {@code path}, resolved since the operation stopped, as it was when it stopped.
     */
    Either<GitFailure, List<ExecutedCommand>> restoreConflict(String path);

    /**
     * Those of {@code paths} that have conflict markers left in them, as files of the working tree, with where. A
     * file that is not there has none. Only read, so not logged.
     */
    Either<GitError, List<MarkedFile>> markers(List<String> paths);

    /**
     * Those of {@code paths} that are in conflict, unmerged in the index. Only read, so not logged.
     */
    Either<GitError, List<String>> unmerged(List<String> paths);

    /**
     * What a file in conflict can be handed to here (ADR 201): whether git has a merge tool set, whether the user set
     * whether it keeps a backup, and whether mergiraf runs. Only read, so not logged.
     */
    Either<GitError, ConflictTools> tools();

    /**
     * Solves what mergiraf can of the conflict in {@code path} (ADR 201): the commands of
     * {@link ConflictTools#mergiraf}. Conflicts left in it are no failure; it stays in conflict, never staged.
     */
    Either<GitFailure, List<ExecutedCommand>> solve(String path);
}
