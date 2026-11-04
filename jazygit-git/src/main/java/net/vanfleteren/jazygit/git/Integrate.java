package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.InProgressAction;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.StackLayer;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Bringing commits from elsewhere onto the checked out branch, as git merge, cherry-pick, revert and rebase do, and
 * going on with what one of those stopped in the middle of (ADR 69).
 */
public interface Integrate {

    /**
     * Merges {@code branch} into the checked out branch, as {@code mode} says. A merge that stops on conflicts is
     * left so, for the user to resolve them, and fails with {@link GitError.StoppedOnConflicts}; a squash merge is
     * then not committed.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> merge(BranchName branch, MergeMode mode);

    /**
     * Commits the changes of the commit {@code sha} on top of the checked out branch. A cherry-pick that stops on
     * conflicts is left so, and fails with {@link GitError.StoppedOnConflicts}; one that stops because it changes
     * nothing is undone again, so the repository is left as it was.
     *
     * @param sha the full object id of a commit that is not a merge
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> cherryPick(Sha sha);

    /**
     * Commits the inverse of {@code commit} on top of the checked out branch, with the message git writes; a merge
     * is reverted against its first parent. A revert that stops on conflicts is left so, and fails with
     * {@link GitError.StoppedOnConflicts}; one that fails otherwise is undone again, so the repository is left as it
     * was.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> revert(Commit commit);

    /**
     * Does {@code action} to {@code inProgress}, the operation the repository stopped in the middle of. Continuing
     * takes the commit message git prepared, without asking; when it stops on the conflicts of a next commit, it
     * fails with {@link GitError.StoppedOnConflicts}.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> continueOrAbort(InProgress inProgress, InProgressAction action);

    /**
     * Rebases the checked out branch onto {@code onto}, bringing uncommitted changes along. A rebase that stops on
     * conflicts is left so, and fails with {@link GitError.StoppedOnConflicts}; when it fails otherwise, it is
     * aborted again, so the repository is left as it was.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> rebase(BranchName onto);

    /**
     * Rebases a stack onto {@code base} in one interactive rebase whose todo picks the own commits of each of
     * {@code layers}, from the bottom up, and moves the branch of each to where its last one lands (ADR 74). It
     * checks out the top, the last of {@code layers}, and brings uncommitted changes along. One that stops on
     * conflicts is left so, as {@link #rebase(BranchName)} is; one that fails otherwise is aborted again.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> rebaseStack(Revision base, List<StackLayer.Commits> layers);
}
