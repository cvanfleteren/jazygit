package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Revision;
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
     * Creates {@code to} at {@code onto}, or else at {@code from}, recording that branch as the one it came from,
     * and checks it out; copies the commits of {@code from} since {@code oldest} onto it, when it starts elsewhere;
     * and moves {@code from} back to the parent of {@code oldest}. Each step only runs when the one before it
     * succeeded, so {@code from} keeps its commits until they are on {@code to}. A cherry-pick that stops on
     * conflicts is left so; one that fails otherwise is aborted, and {@code from} is checked out again and {@code to}
     * deleted, so the repository is left as it was.
     *
     * @return the command lines that were run
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> move(
            Path worktree, BranchName from, Sha oldest, BranchName to, Option<BranchRef> onto) {
        return GitFailure.sequence(
                () -> GitFailure.widen(CliCheckout.checkoutNewBranch(
                        worktree,
                        to,
                        Option.<Revision>narrow(onto).getOrElse(() -> new BranchRef.Local(from)),
                        Option.of(onto.map(CliMoveCommits::name).getOrElse(from)))),
                () -> onto.isDefined()
                        ? copy(worktree, from, oldest)
                                .mapLeft(failure -> undone(
                                        failure,
                                        () -> GitFailure.sequence(
                                                () -> checkout(worktree, from), () -> delete(worktree, to))))
                        : GitFailure.nothing(),
                () -> moveBack(worktree, from, oldest));
    }

    /**
     * Checks out the existing branch {@code to}, copies the commits of {@code from} since {@code oldest} onto it,
     * and moves {@code from} back to the parent of {@code oldest}, each step only when the one before it succeeded.
     * A checkout that uncommitted changes are in the way of fails with {@link GitError.LocalChanges}. A cherry-pick
     * that stops on conflicts is left so; one that fails otherwise is aborted, and {@code from} is checked out again,
     * so the repository is left as it was.
     *
     * @return the command lines that were run
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> moveTo(
            Path worktree, BranchName from, Sha oldest, BranchName to) {
        return GitFailure.sequence(
                () -> CliCheckout.checkout(worktree, to),
                () -> copy(worktree, from, oldest).mapLeft(failure -> undone(failure, () -> checkout(worktree, from))),
                () -> moveBack(worktree, from, oldest));
    }

    /**
     * {@code git cherry-pick --empty=drop --allow-empty <oldest>^..<from>}: copies the commits of {@code from} since
     * {@code oldest} onto the checked out branch. A commit whose changes are on it already is left out, as there is
     * nothing to copy; one that was empty to begin with is copied as it is.
     */
    private static Either<GitFailure<GitError>, List<ExecutedCommand>> copy(
            Path worktree, BranchName from, Sha oldest) {
        return Unfinished.runOrUndo(
                worktree,
                git(
                        "cherry-pick",
                        "--empty=drop",
                        "--allow-empty",
                        parent(oldest) + ".." + GitArgs.revision(new BranchRef.Local(from))),
                left -> Option.when(left.picking(), ABORT));
    }

    /**
     * {@code failure} of the copy, followed by the {@code undo} of what came before it, unless it stopped on
     * conflicts, which are left for the user to resolve on the branch they are copied onto.
     */
    private static GitFailure<GitError> undone(
            GitFailure<GitError> failure, Supplier<Either<GitFailure<ProcessError>, List<ExecutedCommand>>> undo) {
        return failure.error() instanceof GitError.StoppedOnConflicts ? failure : failure.followedBy(undo.get());
    }

    /**
     * {@code git checkout <branch>}: checks out {@code branch} again, which the working tree came from.
     */
    private static Either<GitFailure<ProcessError>, List<ExecutedCommand>> checkout(Path worktree, BranchName branch) {
        return GitProcess.log(worktree, git("checkout", "--quiet", GitArgs.name(branch), "--"));
    }

    /**
     * {@code git branch -D <branch>}: deletes the new {@code branch}, which got none of the commits.
     */
    private static Either<GitFailure<ProcessError>, List<ExecutedCommand>> delete(Path worktree, BranchName branch) {
        return GitProcess.log(worktree, git("branch", "-D", GitArgs.name(branch)));
    }

    /**
     * {@code git branch --force <branch> <oldest>^}: moves {@code branch}, which is not checked out, back to the
     * parent of {@code oldest}.
     */
    private static Either<GitFailure<GitError>, List<ExecutedCommand>> moveBack(
            Path worktree, BranchName branch, Sha oldest) {
        return GitFailure.widen(
                GitProcess.log(worktree, git("branch", "--force", GitArgs.name(branch), parent(oldest))));
    }

    /**
     * The name of {@code branch}; on its remote, the name it has there.
     */
    private static BranchName name(BranchRef branch) {
        return switch (branch) {
            case BranchRef.Local(BranchName name) -> name;
            case BranchRef.Remote(RemoteBranch remote) -> remote.branch();
        };
    }

    private static String parent(Sha oldest) {
        return oldest.value() + "^";
    }
}
