package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * What the repository is in the middle of: a merge, a rebase, a cherry-pick or revert, or conflicts still to
 * resolve. A command that fails half-way leaves such a state behind, which is then undone; one that was there
 * before belongs to the user, and is left alone.
 *
 * @param inProgress the operation that stopped, waiting to be continued or aborted, if any
 * @param conflicted the index holds conflicted files
 */
record Unfinished(Option<InProgress> inProgress, boolean conflicted) {

    /**
     * Reads what the repository in {@code workTree} is in the middle of. Only asked, so not logged.
     */
    static Either<ProcessError, Unfinished> read(Path workTree) {
        return CliInProgress.read(workTree)
                .flatMap(inProgress ->
                        CliMerge.conflicted(workTree).map(conflicted -> new Unfinished(inProgress, conflicted)));
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
    boolean rebasing() {
        return inProgress.exists(InProgress.Rebasing.class::isInstance);
    }

    /**
     * Whether a cherry-pick or revert stopped.
     */
    boolean picking() {
        return inProgress.exists(op -> op instanceof InProgress.CherryPicking || op instanceof InProgress.Reverting);
    }

    /**
     * Runs {@code command}; when it fails, the {@code undo} of what it left unfinished, if any. When the repository
     * was already in the middle of something before, the failure is git refusing to start, and nothing is undone:
     * what is unfinished is the user's. The undo's commands join the failure's, and so does why the undo failed, if
     * it did, or why what was left could not be found out.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> runOrUndo(
            Path workTree, GitCommand command, Function<Unfinished, Option<GitCommand>> undo) {
        return read(workTree)
                .mapLeft(error -> new GitFailure<>(error, List.empty()))
                .flatMap(before -> GitProcess.log(workTree, command)
                        .mapLeft(failure -> before.any()
                                ? failure
                                : uninterrupted(() -> read(workTree)
                                        .fold(
                                                error -> new GitFailure<>(
                                                        failure.error(), failure.commands(), Option.of(error)),
                                                after -> undo.apply(after)
                                                        .map(cleanup ->
                                                                failure.followedBy(GitProcess.log(workTree, cleanup)))
                                                        .getOrElse(failure)))));
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
        return inProgress.isDefined() || conflicted;
    }
}
