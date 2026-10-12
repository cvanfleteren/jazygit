package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.MergeMode;

import java.nio.file.Path;

import static net.vanfleteren.jazygit.git.GitCommand.git;

/**
 * Merges branches through {@code git merge}. A merge that stops on conflicts is undone again, so the
 * repository is left as it was; git itself refuses to start one while changes are staged, or while
 * uncommitted changes would be overwritten.
 */
public final class CliMerge {

    private static final GitCommand ABORT = git("merge", "--abort");
    // A squash leaves no merge to abort, so its conflicts are undone by resetting the merge.
    private static final GitCommand RESET = git("reset", "--merge");

    private CliMerge() {
    }

    /**
     * Merges {@code branch} into the checked out branch as {@code mode} says.
     *
     * @return the command lines that were run
     */
    public static Either<GitFailure<ProcessError>, List<String>> merge(Path workTree, String branch, MergeMode mode) {
        return switch (mode) {
            case FAST_FORWARD -> merge(workTree, git("merge", "--ff", "--no-edit", branch), ABORT);
            case MERGE_COMMIT -> merge(workTree, git("merge", "--no-ff", "--no-edit", branch), ABORT);
            case SQUASH -> squash(workTree, branch);
            case SQUASH_AND_COMMIT -> GitFailure.sequence(() -> squash(workTree, branch), () -> commitStaged(workTree));
        };
    }

    private static Either<GitFailure<ProcessError>, List<String>> squash(Path workTree, String branch) {
        return merge(workTree, git("merge", "--squash", branch), RESET);
    }

    /**
     * Runs {@code merge}, and {@code undo} when it stopped on conflicts. Git's exit code does not tell that
     * apart from refusing to start, which leaves nothing to undo; the conflicted files in the index do. When
     * that cannot be found out, the undo runs anyway: it does nothing harmful when there is nothing to undo.
     */
    private static Either<GitFailure<ProcessError>, List<String>> merge(Path workTree, GitCommand merge,
                                                                      GitCommand undo) {
        return GitProcess.runOrUndo(workTree, merge, undo, () -> conflicted(workTree).getOrElse(true));
    }

    /**
     * Whether the index holds conflicted files. Only asked, so not logged.
     */
    private static Either<ProcessError, Boolean> conflicted(Path workTree) {
        return GitProcess.read(workTree, git("ls-files", "--unmerged"), raw -> Either.right(!raw.isBlank()));
    }

    /**
     * Commits what the squash staged, with git's message listing the squashed commits. A branch that had
     * nothing to squash staged nothing, and then nothing is committed.
     */
    private static Either<GitFailure<ProcessError>, List<String>> commitStaged(Path workTree) {
        // diff --cached --quiet exits with 1, saying nothing, when something is staged.
        return GitProcess.ask(workTree, git("diff", "--cached", "--quiet"))
                .flatMap(nothingStaged -> nothingStaged.isDefined()
                        ? GitFailure.nothing()
                        : GitProcess.log(workTree, git("commit", "--quiet", "--no-edit")));
    }
}
