package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Moves the newest commits of the checked out branch to a new or an existing branch, through
 * {@code git checkout}, then {@code git cherry-pick} when they are copied, and {@code git branch --force}.
 */
final class CliMoveCommits {

    private static final GitCommand ABORT = git("cherry-pick", "--abort");

    private CliMoveCommits() {}

    /**
     * Creates {@code to} at {@code onto}, or else at {@code from}, and checks it out; copies the commits of
     * {@code from} since {@code oldest} onto it, when it starts elsewhere; and moves {@code from} back to the
     * parent of {@code oldest}. Each step only runs when the one before it succeeded, so {@code from} keeps its
     * commits until they are on {@code to}. A cherry-pick that stops on conflicts is left so; one that fails
     * otherwise is aborted.
     *
     * @return the command lines that were run
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> move(
            Path workTree, Branch.Name from, Sha oldest, Branch.Name to, Option<String> onto) {
        return GitFailure.sequence(
                () -> GitFailure.widen(CliCheckout.checkoutNewBranch(workTree, to, onto.getOrElse(from.value()))),
                () -> onto.isDefined() ? copy(workTree, from, oldest) : GitFailure.nothing(),
                () -> moveBack(workTree, from, oldest));
    }

    /**
     * Checks out the existing branch {@code to}, copies the commits of {@code from} since {@code oldest} onto it,
     * and moves {@code from} back to the parent of {@code oldest}, each step only when the one before it succeeded.
     * With {@code stash}, the uncommitted changes are taken along through a stash; without, a checkout they are in
     * the way of fails with {@link GitError.LocalChanges}. A cherry-pick that stops on conflicts is left so; one that
     * fails otherwise is aborted.
     *
     * @return the command lines that were run
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> moveTo(
            Path workTree, Branch.Name from, Sha oldest, Branch.Name to, boolean stash) {
        return GitFailure.sequence(
                () -> stash ? CliCheckout.checkoutWithStash(workTree, to) : CliCheckout.checkout(workTree, to),
                () -> copy(workTree, from, oldest),
                () -> moveBack(workTree, from, oldest));
    }

    /**
     * {@code git cherry-pick <oldest>^..<from>}: copies the commits of {@code from} since {@code oldest} onto the
     * checked out branch.
     */
    private static Either<GitFailure<GitError>, List<ExecutedCommand>> copy(
            Path workTree, Branch.Name from, Sha oldest) {
        return Unfinished.runOrUndo(
                workTree,
                git("cherry-pick", parent(oldest) + ".." + Refs.head(from)),
                left -> Option.when(left.picking(), ABORT));
    }

    /**
     * {@code git branch --force <branch> <oldest>^}: moves {@code branch}, which is not checked out, back to the
     * parent of {@code oldest}.
     */
    private static Either<GitFailure<GitError>, List<ExecutedCommand>> moveBack(
            Path workTree, Branch.Name branch, Sha oldest) {
        return GitFailure.widen(GitProcess.log(workTree, git("branch", "--force", branch.value(), parent(oldest))));
    }

    private static String parent(Sha oldest) {
        return oldest.value() + "^";
    }
}
