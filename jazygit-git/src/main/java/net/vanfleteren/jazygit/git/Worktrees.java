package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.NewBranchStart;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Listing, adding and removing the worktrees of the repository, as git worktree does (ADR 69).
 */
public interface Worktrees {

    /**
     * The worktrees, the one shown first, then the others as git lists them, the main one first; none for a bare
     * repository itself, which has no working tree.
     */
    Either<ProcessError, List<Worktree>> list();

    /**
     * Whether the worktree at {@code worktree} has changes that removing it would lose: staged, unstaged or
     * untracked files, not ignored ones.
     */
    Either<ProcessError, Boolean> hasChanges(Path worktree);

    /**
     * Checks {@code branch} out in a new worktree at {@code location}, which is taken relative to the working
     * tree of this one. Git refuses a branch that is checked out already, and a location that is not empty.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> add(Path location, BranchName branch);

    /**
     * Checks {@code startPoint} out in a new worktree at {@code location}, taken as {@link #add} takes it, on no
     * branch.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> addDetached(Path location, Revision startPoint);

    /**
     * Creates the branch {@code name} where {@code start} says and checks it out in a new worktree at
     * {@code location}, taken as {@link #add} takes it. A {@code parent} it gives is recorded as the branch it was
     * made from. Git refuses a name that is a branch already.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> addWithNewBranch(
            Path location, BranchName name, NewBranchStart start);

    /**
     * Removes the linked worktree at {@code location}, its directory included; its branch stays. Without
     * {@code force}, git refuses one with modified or untracked files, which is a
     * {@link GitError.WorktreeHasChanges}.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> remove(Path location, boolean force);

    /**
     * Removes the linked worktree at {@code location} whatever it has: also when it has changes, which are lost,
     * when it is locked, and when its directory is gone. Its branch stays.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> removeAnyway(Path location);

    /**
     * Forgets the worktrees whose directory is gone; their branches stay.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> prune();

    /**
     * Locks the linked worktree at {@code location}, which keeps git from removing or pruning it, or unlocks it
     * when not {@code lock}. Git refuses the main worktree, and one that is locked already or not locked.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> lock(Path location, boolean lock);

    /**
     * Locks the linked worktree at {@code location} for {@code reason}, or without one when it is blank. Git refuses
     * the main worktree, and one that is locked already.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> lock(Path location, String reason);
}
