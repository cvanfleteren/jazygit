package net.vanfleteren.jazygit.git.commits;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.branches.Branches;
import net.vanfleteren.jazygit.git.history.Integrate;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.ResetMode;
import net.vanfleteren.jazygit.git.model.ResetTarget;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.SquashMode;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.git.process.GitFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Creating and rewriting commits, as git commit does, and through an interactive rebase for older commits; moving
 * commits to another branch; and moving the checked out branch, as git reset does (ADR 69).
 */
public interface Commits {

    /**
     * Commits what is staged.
     */
    Either<GitFailure, List<ExecutedCommand>> commit(CommitMessage message);

    /**
     * Amends the last commit with what is staged, keeping its message.
     */
    Either<GitFailure, List<ExecutedCommand>> amendLast();

    /**
     * Amends the commit {@code sha} with what is staged, keeping its message. Other than for the last commit, this
     * rebases the commits after it, unstaged changes being stashed meanwhile; the rebase is left as it stopped on
     * conflicts, see {@link Integrate#rebase}, and aborted when it fails otherwise, leaving the staged changes
     * staged.
     *
     * @param sha the full object id of a commit that is not a merge
     */
    Either<GitFailure, List<ExecutedCommand>> amend(Sha sha);

    /**
     * Replaces the message of the commit {@code sha}, leaving its content alone. Other than for the last commit,
     * this rebases the commits after it, uncommitted changes being stashed meanwhile; the rebase is left
     * as it stopped on conflicts, see {@link Integrate#rebase}, and aborted when it fails otherwise.
     *
     * @param sha the full object id of the commit
     */
    Either<GitFailure, List<ExecutedCommand>> reword(Sha sha, CommitMessage message);

    /**
     * Folds the commit {@code sha} into its parent, keeping the messages {@code mode} says. This rebases the
     * commits after it, uncommitted changes being stashed meanwhile; the rebase is left as it stopped on
     * conflicts, see {@link Integrate#rebase}, and aborted when it fails otherwise.
     *
     * @param sha the full object id of a commit that has exactly one parent
     */
    Either<GitFailure, List<ExecutedCommand>> squash(Sha sha, SquashMode mode);

    /**
     * Removes the commit {@code sha} from the history of the checked out branch. This rebases the commits after
     * it, uncommitted changes being stashed meanwhile; the rebase is left as it stopped on conflicts, see
     * {@link Integrate#rebase}, and aborted when it fails otherwise.
     *
     * @param sha the full object id of a commit that is not a merge
     */
    Either<GitFailure, List<ExecutedCommand>> drop(Sha sha);

    // Moving commits to another branch, as git checkout, cherry-pick and branch do.

    /**
     * Moves the commits of the checked out branch {@code from}, from {@code oldest} up to its tip, to the new
     * branch {@code to}, which is then checked out, and moves {@code from} back to the parent of {@code oldest}.
     * Without {@code onto}, the new branch starts where {@code from} is, so nothing is copied and the working tree
     * is left alone. With it, the new branch starts at {@code onto}, and the commits are cherry-picked onto it;
     * a cherry-pick that stops on conflicts is left so, and fails with {@link GitError.StoppedOnConflicts}, and
     * {@code from} is only moved back once they are all copied. The new branch records the branch it starts at as
     * the one it came from, as {@link Branches#parent} reads it.
     *
     * @param oldest the full object id of a commit of {@code from} that has a parent
     * @param onto   the branch the new branch starts at, if not at {@code from}
     */
    Either<GitFailure, List<ExecutedCommand>> move(BranchName from, Sha oldest, BranchName to, Option<BranchRef> onto);

    /**
     * Moves the commits of the checked out branch {@code from}, from {@code oldest} up to its tip, onto the existing
     * branch {@code to}, which is then checked out, and moves {@code from} back to the parent of {@code oldest}. A
     * checkout that uncommitted changes are in the way of fails with {@link GitError.LocalChanges}. A cherry-pick
     * that stops on conflicts is left so, and fails with {@link GitError.StoppedOnConflicts}, and {@code from} is
     * only moved back once the commits are all copied.
     *
     * @param oldest the full object id of a commit of {@code from} that has a parent
     */
    Either<GitFailure, List<ExecutedCommand>> moveTo(BranchName from, Sha oldest, BranchName to);

    // Moving the checked out branch, as git reset does.

    /**
     * Moves the checked out branch to {@code target}, doing with the changes {@code mode} says.
     */
    Either<GitFailure, List<ExecutedCommand>> reset(ResetTarget target, ResetMode mode);
}
