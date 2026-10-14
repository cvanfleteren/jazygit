package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.MergeMode;

import java.nio.file.Path;

import static net.vanfleteren.jazygit.git.GitCommand.git;

/**
 * Merges branches through {@code git merge}. A merge that stops on conflicts is undone again, so the
 * repository is left as it was; git itself refuses to start one while changes are staged, while
 * uncommitted changes would be overwritten, or while the repository is in the middle of something else,
 * which is then left alone.
 */
public final class CliMerge {

    static final GitCommand ABORT = git("merge", "--abort");
    // A squash leaves no merge to abort, so its conflicts are undone by resetting the merge.
    private static final GitCommand RESET = git("reset", "--merge");

    private CliMerge() {
    }

    /**
     * Merges {@code branch} into the checked out branch as {@code mode} says.
     *
     * @return the command lines that were run
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> merge(Path workTree, Branch.Name branch, MergeMode mode) {
        return switch (mode) {
            case FAST_FORWARD -> merge(workTree, git("merge", "--ff", "--no-edit", branch.value()), ABORT);
            case MERGE_COMMIT -> merge(workTree, git("merge", "--no-ff", "--no-edit", branch.value()), ABORT);
            case SQUASH -> squash(workTree, branch);
            case SQUASH_AND_COMMIT -> GitFailure.sequence(() -> squash(workTree, branch), () -> commitStaged(workTree));
        };
    }

    private static Either<GitFailure<ProcessError>, List<ExecutedCommand>> squash(Path workTree, Branch.Name branch) {
        return merge(workTree, git("merge", "--squash", branch.value()), RESET);
    }

    /**
     * Runs {@code merge}, and {@code undo} when it stopped on conflicts. Git's exit code does not tell that
     * apart from refusing to start, which leaves nothing to undo; the conflicted files in the index do.
     */
    private static Either<GitFailure<ProcessError>, List<ExecutedCommand>> merge(Path workTree, GitCommand merge,
                                                                      GitCommand undo) {
        return Unfinished.runOrUndo(workTree, merge, left -> Option.when(left.conflicted(), undo));
    }

    /**
     * Whether the index holds conflicted files. Only asked, so not logged.
     */
    static Either<ProcessError, Boolean> conflicted(Path workTree) {
        return GitProcess.read(workTree, GitCommand.reading("ls-files", "--unmerged"), raw -> Either.right(!raw.isBlank()));
    }

    /**
     * Commits what the squash staged, with git's message listing the squashed commits. A branch that had
     * nothing to squash staged nothing, and then nothing is committed.
     */
    private static Either<GitFailure<ProcessError>, List<ExecutedCommand>> commitStaged(Path workTree) {
        // diff --cached --quiet exits with 1, saying nothing, when something is staged.
        return GitProcess.ask(workTree, GitCommand.reading("diff", "--cached", "--quiet"))
                .flatMap(nothingStaged -> nothingStaged.isDefined()
                        ? GitFailure.nothing()
                        : GitProcess.log(workTree, git("commit", "--quiet", "--no-edit")));
    }
}
