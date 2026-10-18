package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Moves the newest commits of the checked out branch to a new branch, through {@code git checkout -b}, then
 * {@code git cherry-pick} when the new branch starts elsewhere, and {@code git branch --force}.
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
        String parent = oldest.value() + "^";
        return GitFailure.sequence(
                () -> GitFailure.widen(CliCheckout.checkoutNewBranch(workTree, to, onto.getOrElse(from.value()))),
                () -> onto.isDefined()
                        ? Unfinished.runOrUndo(
                                workTree,
                                git("cherry-pick", parent + ".." + Refs.head(from)),
                                left -> Option.when(left.picking(), ABORT))
                        : GitFailure.nothing(),
                () -> GitFailure.<GitError, List<ExecutedCommand>>widen(moveBack(workTree, from, parent)));
    }

    /**
     * {@code git branch --force <branch> <revision>}: moves {@code branch}, which is not checked out, to
     * {@code revision}.
     */
    private static Either<GitFailure<ProcessError>, List<ExecutedCommand>> moveBack(
            Path workTree, Branch.Name branch, String revision) {
        return GitProcess.log(workTree, git("branch", "--force", branch.value(), revision));
    }
}
