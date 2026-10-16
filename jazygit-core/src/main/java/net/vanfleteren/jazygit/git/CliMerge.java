package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Merges branches through {@code git merge}. A merge that stops on conflicts is left so, for the user to resolve
 * them; git itself refuses to start one while changes are staged, while uncommitted changes would be overwritten,
 * or while the repository is in the middle of something else, which is then left alone.
 */
final class CliMerge {

    private CliMerge() {}

    /**
     * Merges {@code branch} into the checked out branch as {@code mode} says. A squash merge that stops on
     * conflicts is not committed: git keeps no merge to continue, so the user commits it once they are resolved.
     *
     * @return the command lines that were run
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> merge(
            Path workTree, Branch.Name branch, MergeMode mode) {
        return switch (mode) {
            case FAST_FORWARD -> Unfinished.runOrStop(workTree, git("merge", "--ff", "--no-edit", branch.value()));
            case MERGE_COMMIT -> Unfinished.runOrStop(workTree, git("merge", "--no-ff", "--no-edit", branch.value()));
            case SQUASH -> squash(workTree, branch);
            case SQUASH_AND_COMMIT ->
                GitFailure.sequence(() -> squash(workTree, branch), () -> GitFailure.widen(commitStaged(workTree)));
        };
    }

    private static Either<GitFailure<GitError>, List<ExecutedCommand>> squash(Path workTree, Branch.Name branch) {
        return Unfinished.runOrStop(workTree, git("merge", "--squash", branch.value()));
    }

    /**
     * How many files in the index have conflicts. Only asked, so not logged.
     */
    static Either<ProcessError, Integer> conflicts(Path workTree) {
        // "<mode> <object> <stage>\t<path>", a line for each stage of each conflicted path.
        return GitProcess.read(
                workTree,
                GitCommand.reading("ls-files", "--unmerged"),
                raw -> Either.right(List.of(raw.split("\n"))
                        .filter(line -> line.contains("\t"))
                        .map(line -> line.substring(line.indexOf('\t') + 1))
                        .distinct()
                        .size()));
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
