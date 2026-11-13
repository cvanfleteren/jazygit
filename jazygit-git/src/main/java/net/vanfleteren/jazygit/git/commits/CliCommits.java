package net.vanfleteren.jazygit.git.commits;

import static net.vanfleteren.jazygit.git.process.GitCommand.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.git.branches.CliCheckout;
import net.vanfleteren.jazygit.git.history.CliHistory;
import net.vanfleteren.jazygit.git.history.CliInProgress;
import net.vanfleteren.jazygit.git.history.CliRebase;
import net.vanfleteren.jazygit.git.history.Unfinished;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.ResetMode;
import net.vanfleteren.jazygit.git.model.ResetTarget;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.SquashMode;
import net.vanfleteren.jazygit.git.process.GitArgs;
import net.vanfleteren.jazygit.git.process.GitCommand;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.git.process.GitFailure;
import net.vanfleteren.jazygit.git.process.GitProcess;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * The {@link Commits} of a repository: creates and rewrites commits through {@code git commit}, and through
 * {@link CliRebase} for commits before the last one; moves the newest commits of the checked out branch to another
 * branch through {@code git checkout}, then {@code git cherry-pick} when they are copied, and
 * {@code git branch --force}; and moves the checked out branch through {@code git reset}.
 */
public final class CliCommits implements Commits {

    private static final GitCommand ABORT_PICK = git("cherry-pick", "--abort");

    private final Path worktree;

    public CliCommits(Path worktree) {
        this.worktree = worktree;
    }

    /**
     * {@code git commit -m <summary> [-m <description>]}: commits the index.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> commit(CommitMessage message) {
        return run(List.of("commit", "--quiet").appendAll(messageArgs(message)));
    }

    /**
     * {@code git commit --amend --no-edit}: amends the last commit with the index.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> amendLast() {
        return run(List.of("commit", "--quiet", "--amend", "--no-edit"));
    }

    /**
     * Amends the commit {@code sha} with the index, keeping its message: by amending when it is HEAD, else by
     * committing the index as a fixup of it, which {@link CliRebase#fixupInto} folds into it. When that fails other
     * than on conflicts, the fixup commit is undone again, so its changes are staged as before. When it stops on
     * conflicts, the rebase is marked so that aborting it undoes the fixup commit too.
     *
     * @return the command lines that were run
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> amend(Sha sha) {
        return CliHistory.resolve(worktree, "HEAD")
                .flatMap(head -> head.contains(sha)
                        ? amendLast()
                        : GitFailure.sequence(
                                () -> run(List.of("commit", "--quiet", "--fixup=" + sha.value())),
                                () -> CliRebase.fixupInto(worktree, sha)
                                        .mapLeft(failure -> failure.error() instanceof GitError.StoppedOnConflicts
                                                ? markedAmending(failure)
                                                : failure.followedBy(
                                                        run(List.of("reset", "--quiet", "--soft", "HEAD^"))))));
    }

    /**
     * {@code stop}, once the rebase it stopped is marked as one that amends, see
     * {@link CliInProgress#markAmending}.
     */
    private GitFailure markedAmending(GitFailure stop) {
        CliInProgress.markAmending(worktree);
        return stop;
    }

    /**
     * Replaces the message of {@code sha}: by amending when it is HEAD, else by {@link CliRebase#reword}.
     *
     * @return the command lines that were run
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> reword(Sha sha, CommitMessage message) {
        return CliHistory.resolve(worktree, "HEAD")
                .flatMap(head -> head.contains(sha) ? rewordHead(message) : CliRebase.reword(worktree, sha, message));
    }

    /**
     * Folds the commit {@code sha} into its parent, as {@link CliRebase#squash} does.
     *
     * @param sha the object id of a commit that has exactly one parent
     * @return the command lines that were run
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> squash(Sha sha, SquashMode mode) {
        return CliRebase.squash(worktree, sha, mode);
    }

    /**
     * Removes the commit {@code sha}, as {@link CliRebase#drop} does.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> drop(Sha sha) {
        return CliRebase.drop(worktree, sha);
    }

    /**
     * {@code git commit --allow-empty --amend --only -m <summary> [-m <description>]}: replaces the
     * message of the last commit, whatever is staged.
     */
    private Either<GitFailure, List<ExecutedCommand>> rewordHead(CommitMessage message) {
        return run(List.of("commit", "--quiet", "--allow-empty", "--amend", "--only")
                .appendAll(messageArgs(message)));
    }

    private static List<String> messageArgs(CommitMessage message) {
        return message.hasDescription()
                ? List.of("-m", message.summary(), "-m", message.description())
                : List.of("-m", message.summary());
    }

    private Either<GitFailure, List<ExecutedCommand>> run(List<String> args) {
        return GitProcess.log(worktree, git(args));
    }

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
    @Override
    public Either<GitFailure, List<ExecutedCommand>> move(
            BranchName from, Sha oldest, BranchName to, Option<BranchRef> onto) {
        return GitFailure.sequence(
                () -> CliCheckout.checkoutNewBranch(
                        worktree,
                        to,
                        Option.<Revision>narrow(onto).getOrElse(() -> new BranchRef.Local(from)),
                        Option.of(onto.map(CliCommits::name).getOrElse(from))),
                () -> onto.isDefined()
                        ? copy(from, oldest)
                                .mapLeft(failure -> undone(
                                        failure, () -> GitFailure.sequence(() -> checkout(from), () -> delete(to))))
                        : GitFailure.nothing(),
                () -> moveBack(from, oldest));
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
    @Override
    public Either<GitFailure, List<ExecutedCommand>> moveTo(BranchName from, Sha oldest, BranchName to) {
        return GitFailure.sequence(
                () -> CliCheckout.checkout(worktree, to),
                () -> copy(from, oldest).mapLeft(failure -> undone(failure, () -> checkout(from))),
                () -> moveBack(from, oldest));
    }

    /**
     * {@code git cherry-pick --empty=drop --allow-empty <oldest>^..<from>}: copies the commits of {@code from} since
     * {@code oldest} onto the checked out branch. A commit whose changes are on it already is left out, as there is
     * nothing to copy; one that was empty to begin with is copied as it is.
     */
    private Either<GitFailure, List<ExecutedCommand>> copy(BranchName from, Sha oldest) {
        return Unfinished.runOrUndo(
                worktree,
                git(
                        "cherry-pick",
                        "--empty=drop",
                        "--allow-empty",
                        parent(oldest) + ".." + GitArgs.revision(new BranchRef.Local(from))),
                left -> Option.when(left.picking(), ABORT_PICK));
    }

    /**
     * {@code failure} of the copy, followed by the {@code undo} of what came before it, unless it stopped on
     * conflicts, which are left for the user to resolve on the branch they are copied onto.
     */
    private static GitFailure undone(GitFailure failure, Supplier<Either<GitFailure, List<ExecutedCommand>>> undo) {
        return failure.error() instanceof GitError.StoppedOnConflicts ? failure : failure.followedBy(undo.get());
    }

    /**
     * {@code git checkout <branch>}: checks out {@code branch} again, which the working tree came from.
     */
    private Either<GitFailure, List<ExecutedCommand>> checkout(BranchName branch) {
        return GitProcess.log(worktree, git("checkout", "--quiet", GitArgs.name(branch), "--"));
    }

    /**
     * {@code git branch -D <branch>}: deletes the new {@code branch}, which got none of the commits.
     */
    private Either<GitFailure, List<ExecutedCommand>> delete(BranchName branch) {
        return GitProcess.log(worktree, git("branch", "-D", GitArgs.name(branch)));
    }

    /**
     * {@code git branch --force <branch> <oldest>^}: moves {@code branch}, which is not checked out, back to the
     * parent of {@code oldest}.
     */
    private Either<GitFailure, List<ExecutedCommand>> moveBack(BranchName branch, Sha oldest) {
        return GitProcess.log(worktree, git("branch", "--force", GitArgs.name(branch), parent(oldest)));
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

    /**
     * {@code git reset --quiet <mode> <revision>}: moves the checked out branch to {@code target}.
     *
     * @return the command lines that were run
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> reset(ResetTarget target, ResetMode mode) {
        return GitProcess.log(worktree, git("reset", "--quiet", mode.option(), GitArgs.revision(target.revision())));
    }
}
