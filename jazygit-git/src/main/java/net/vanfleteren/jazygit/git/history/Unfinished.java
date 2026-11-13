package net.vanfleteren.jazygit.git.history;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.process.GitCommand;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.git.process.GitError.StoppedOnConflicts;
import net.vanfleteren.jazygit.git.process.GitFailure;
import net.vanfleteren.jazygit.git.process.GitProcess;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * What the repository is in the middle of: a merge, a rebase, a cherry-pick or revert, or conflicts still to
 * resolve. A command that stops on conflicts is left so, for the user to resolve them; one that stops half-way
 * otherwise is undone. What was unfinished before belongs to the user, and is left alone.
 *
 * @param inProgress the operation that stopped, waiting to be continued or aborted, if any
 * @param conflicts  how many files in the index have conflicts
 */
public record Unfinished(Option<InProgress> inProgress, int conflicts) {

    /**
     * Reads what the repository in {@code worktree} is in the middle of. Only asked, so not logged.
     */
    static Either<GitError, Unfinished> read(Path worktree) {
        return CliInProgress.read(worktree)
                .flatMap(inProgress ->
                        CliIntegrate.conflicts(worktree).map(conflicts -> new Unfinished(inProgress, conflicts)));
    }

    /**
     * Whether the index holds conflicted files.
     */
    boolean conflicted() {
        return conflicts > 0;
    }

    /**
     * Whether a merge stopped, waiting to be committed or aborted.
     */
    boolean merging() {
        return inProgress.exists(InProgress.Merging.class::isInstance);
    }

    /**
     * Whether a rebase stopped, waiting to be continued or aborted.
     */
    public boolean rebasing() {
        return inProgress.exists(InProgress.Rebasing.class::isInstance);
    }

    /**
     * Whether a cherry-pick or revert stopped.
     */
    public boolean picking() {
        return inProgress.exists(op -> op instanceof InProgress.CherryPicking || op instanceof InProgress.Reverting);
    }

    /**
     * Runs {@code command}. When it stops on conflicts, it is left so, and reported as {@link StoppedOnConflicts};
     * when it fails otherwise, the {@code undo} of what it left unfinished is run, if any. When the repository was
     * already in the middle of something before, the failure is git refusing to start, and nothing is undone: what
     * is unfinished is the user's. The undo's commands join the failure's, and so does why the undo failed, if it
     * did, or why what was left could not be found out.
     */
    public static Either<GitFailure, List<ExecutedCommand>> runOrUndo(
            Path worktree, GitCommand command, Function<Unfinished, Option<GitCommand>> undo) {
        return read(worktree)
                .mapLeft(error -> new GitFailure(error, List.empty()))
                .flatMap(before -> GitProcess.log(worktree, command)
                        .mapLeft(failure -> before.any()
                                ? failure
                                : uninterrupted(() -> read(worktree)
                                        .<GitFailure>fold(
                                                error -> new GitFailure(
                                                        failure.error(), failure.commands(), Option.of(error)),
                                                after -> left(worktree, failure, after, undo)))));
    }

    /**
     * Runs {@code command}, and leaves it as it stopped when it stops on conflicts; see {@link #runOrUndo}.
     */
    static Either<GitFailure, List<ExecutedCommand>> runOrStop(Path worktree, GitCommand command) {
        return runOrUndo(worktree, command, after -> Option.none());
    }

    /**
     * What becomes of {@code failure}, which left {@code after} behind: a stop on conflicts, or the failure
     * followed by its {@code undo}.
     */
    private static GitFailure left(
            Path worktree, GitFailure failure, Unfinished after, Function<Unfinished, Option<GitCommand>> undo) {
        return after.conflicted()
                ? after.stopped(failure)
                : undo.apply(after)
                        .map(cleanup -> failure.followedBy(GitProcess.log(worktree, cleanup)))
                        .getOrElse(failure);
    }

    /**
     * {@code failure} as the stop on conflicts it is, when it got past {@code from} and stopped on the conflicts
     * of a next step; else as it is.
     */
    static GitFailure stoppedAgain(Path worktree, InProgress from, GitFailure failure) {
        return read(worktree)
                .toOption()
                .filter(after -> after.conflicted() && after.inProgress().exists(now -> !now.equals(from)))
                .map(after -> after.stopped(failure))
                .getOrElse(() -> failure);
    }

    /**
     * The commands of {@code failure}, as a stop on these conflicts.
     */
    private GitFailure stopped(GitFailure failure) {
        return new GitFailure(new StoppedOnConflicts(inProgress, conflicts), failure.commands());
    }

    /**
     * Runs {@code cleanup} as if the thread was not interrupted: a command that was, was killed half-way, and
     * what it left still has to be undone, which would else be killed right away too. The interrupt is kept.
     */
    private static <T> T uninterrupted(Supplier<T> cleanup) {
        boolean interrupted = Thread.interrupted();
        try {
            return cleanup.get();
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Whether anything is unfinished.
     */
    boolean any() {
        return inProgress.isDefined() || conflicted();
    }
}
