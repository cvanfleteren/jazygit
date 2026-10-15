package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.process.ExecutedCommand;

import java.nio.file.Path;

/**
 * Listing, adding and removing the worktrees of the repository, as {@code git worktree} does. Each change returns
 * the command lines it ran, or why it failed together with the command lines run up to then.
 */
public interface GitWorktree {

    /**
     * The worktrees, the main one first; none for a bare repository itself, which has no working tree.
     */
    Either<ProcessError, List<Worktree>> worktrees();

    /**
     * Checks {@code branch} out in a new worktree at {@code location}, which is taken relative to the working
     * tree of this one. Git refuses a branch that is checked out already, and a location that is not empty.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> addWorktree(Path location, Branch.Name branch);

    /**
     * Removes the linked worktree at {@code location}, its directory included; its branch stays. Without
     * {@code force}, git refuses one with modified or untracked files, which is a
     * {@link GitError.WorktreeHasChanges}.
     */
    Either<GitFailure<GitError.RemoveWorktreeError>, List<ExecutedCommand>> removeWorktree(Path location, boolean force);
}
