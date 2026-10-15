package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.process.ExecutedCommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;

import static net.vanfleteren.jazygit.git.GitCommand.reading;

/**
 * What the repository is in the middle of: a merge, a rebase, a cherry-pick or revert, or conflicts still to
 * resolve. A command that fails half-way leaves such a state behind, which is then undone; one that was there
 * before belongs to the user, and is left alone.
 *
 * @param merging    a merge stopped, waiting to be committed or aborted
 * @param rebasing   a rebase stopped, waiting to be continued or aborted
 * @param picking    a cherry-pick or revert stopped
 * @param conflicted the index holds conflicted files
 */
record Unfinished(boolean merging, boolean rebasing, boolean picking, boolean conflicted) {

    // Where git keeps what it is in the middle of; --git-path finds them in a linked worktree too.
    private static final List<String> MARKERS = List.of("MERGE_HEAD", "rebase-merge", "rebase-apply",
            "CHERRY_PICK_HEAD", "REVERT_HEAD");

    /**
     * Reads what the repository in {@code workTree} is in the middle of. Only asked, so not logged.
     */
    static Either<ProcessError, Unfinished> read(Path workTree) {
        return GitProcess.read(workTree, reading(MARKERS.flatMap(marker -> List.of("--git-path", marker)).prepend("rev-parse")),
                        raw -> paths(raw, workTree))
                .flatMap(markers -> CliMerge.conflicted(workTree).map(conflicted -> new Unfinished(
                        markers.get(0), markers.get(1) || markers.get(2), markers.get(3) || markers.get(4), conflicted)));
    }

    private static Either<UnexpectedOutput, List<Boolean>> paths(String raw, Path workTree) {
        List<String> lines = List.of(raw.split("\n")).filter(line -> !line.isEmpty());
        return lines.size() == MARKERS.size()
                ? Either.right(lines.map(line -> Files.exists(workTree.resolve(line))))
                : Either.left(new UnexpectedOutput(raw));
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
                        .mapLeft(failure -> before.any() ? failure : uninterrupted(() -> read(workTree).fold(
                                error -> new GitFailure<>(failure.error(), failure.commands(), Option.of(error)),
                                after -> undo.apply(after)
                                        .map(cleanup -> failure.followedBy(GitProcess.log(workTree, cleanup)))
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
        return merging || rebasing || picking || conflicted;
    }
}
