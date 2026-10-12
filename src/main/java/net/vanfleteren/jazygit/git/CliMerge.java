package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.CommandFailed;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitProcess.Output;
import net.vanfleteren.jazygit.git.model.MergeMode;

import java.nio.file.Path;

import static net.vanfleteren.jazygit.git.GitCommand.git;

/**
 * Merges branches through {@code git merge}. A merge that stops on conflicts is undone again, so the
 * repository is left as it was; git itself refuses to start one while changes are staged, or while
 * uncommitted changes would be overwritten.
 */
public final class CliMerge {

    private CliMerge() {
    }

    /**
     * Merges {@code branch} into the checked out branch as {@code mode} says.
     *
     * @return the command lines that were run
     */
    public static Either<GitFailure<ProcessError>, List<String>> merge(Path workTree, String branch, MergeMode mode) {
        return switch (mode) {
            case FAST_FORWARD -> merge(workTree, git("merge", "--ff", "--no-edit", branch), git("merge", "--abort"));
            case MERGE_COMMIT -> merge(workTree, git("merge", "--no-ff", "--no-edit", branch), git("merge", "--abort"));
            case SQUASH -> squash(workTree, branch);
            case SQUASH_AND_COMMIT -> squash(workTree, branch).flatMap(squashed -> commitStaged(workTree)
                    .map(squashed::appendAll)
                    .mapLeft(failure -> failure.after(squashed)));
        };
    }

    /**
     * A squash leaves no merge to abort, so its conflicts are undone by resetting the merge.
     */
    private static Either<GitFailure<ProcessError>, List<String>> squash(Path workTree, String branch) {
        return merge(workTree, git("merge", "--squash", branch), git("reset", "--merge"));
    }

    /**
     * Runs {@code merge}, and {@code undo} when it stopped on conflicts. Git's exit code does not tell that
     * apart from refusing to start, which leaves nothing to undo; the conflicted files in the index do.
     */
    private static Either<GitFailure<ProcessError>, List<String>> merge(Path workTree, GitCommand merge,
                                                                      GitCommand undo) {
        return GitProcess.run(workTree, merge)
                .map(Output::log)
                .mapLeft(failure -> conflicted(workTree)
                        ? failure.followedBy(GitProcess.attempt(workTree, undo))
                        : failure);
    }

    /**
     * Whether the index holds conflicted files. Only asked, so not logged.
     */
    private static boolean conflicted(Path workTree) {
        return GitProcess.run(workTree, git("ls-files", "--unmerged"))
                .exists(output -> !output.stdout().isBlank());
    }

    /**
     * Commits what the squash staged, with git's message listing the squashed commits. A branch that had
     * nothing to squash staged nothing, and then nothing is committed.
     */
    private static Either<GitFailure<ProcessError>, List<String>> commitStaged(Path workTree) {
        // diff --cached --quiet exits with 1, saying nothing, when something is staged.
        return GitProcess.run(workTree, git("diff", "--cached", "--quiet"))
                .fold(failure -> failure.error() instanceof CommandFailed(String explanation, int exitCode)
                                && exitCode == 1 && explanation.isEmpty()
                                ? GitProcess.run(workTree, git("commit", "--quiet", "--no-edit")).map(Output::log)
                                : Either.left(failure),
                        nothingStaged -> Either.right(List.empty()));
    }
}
