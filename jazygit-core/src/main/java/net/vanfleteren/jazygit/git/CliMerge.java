package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Either;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Merges branches through {@code git merge}. A merge that stops on conflicts is left so, for the user to resolve
 * them; git itself refuses to start one while changes are staged, while uncommitted changes would be overwritten,
 * or while the repository is in the middle of something else, which is then left alone.
 */
final class CliMerge {

    // The branches git does not name in a merge commit into them. Git also honours merge.suppressDest here, which
    // is not read, so a user who set it still gets "into <branch>".
    private static final Set<String> DEFAULT_BRANCHES = HashSet.of("main", "master");

    private CliMerge() {}

    /**
     * Merges {@code branch} into the checked out branch as {@code mode} says. A squash merge that stops on
     * conflicts is not committed: git keeps no merge to continue, so the user commits it once they are resolved.
     *
     * @return the command lines that were run
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> merge(Path worktree, BranchName branch, MergeMode mode) {
        return switch (mode) {
            case FAST_FORWARD -> withMessage(worktree, branch, "--ff");
            case MERGE_COMMIT -> withMessage(worktree, branch, "--no-ff");
            case SQUASH -> squash(worktree, branch);
            case SQUASH_AND_COMMIT ->
                GitFailure.sequence(() -> squash(worktree, branch), () -> GitFailure.widen(commitStaged(worktree)));
        };
    }

    /**
     * Merges the ref of {@code branch}, as a tag named like the branch would win over its bare name. Git would then
     * word the merge commit after the ref, so the message is given: the one git writes for a branch, which names
     * the branch merged into unless that is a default one. This is built by hand, so it deviates from git where
     * config changes the wording: {@code merge.suppressDest} is ignored, and {@code merge.branchdesc} is not used.
     * With {@code merge.log} on, git's shortlog header names the ref instead of the branch.
     */
    private static Either<GitFailure<GitError>, List<ExecutedCommand>> withMessage(
            Path worktree, BranchName branch, String fastForward) {
        return GitProcess.read(
                        worktree, GitCommand.reading("branch", "--show-current"), raw -> Either.right(raw.strip()))
                .<GitFailure<GitError>>mapLeft(error -> new GitFailure<>(error, List.empty()))
                .flatMap(current -> Unfinished.runOrStop(
                        worktree,
                        git("merge", fastForward, "--no-edit", "-m", message(branch, current), Refs.head(branch))));
    }

    private static String message(BranchName branch, String current) {
        // On a detached HEAD git merges "into HEAD", and `branch --show-current` prints nothing.
        String into = current.isEmpty() ? "HEAD" : current;
        return "Merge branch '" + branch.value() + "'" + (DEFAULT_BRANCHES.contains(into) ? "" : " into " + into);
    }

    private static Either<GitFailure<GitError>, List<ExecutedCommand>> squash(Path worktree, BranchName branch) {
        return Unfinished.runOrStop(worktree, git("merge", "--squash", Refs.head(branch)));
    }

    /**
     * How many files in the index have conflicts. Only asked, so not logged.
     */
    static Either<ProcessError, Integer> conflicts(Path worktree) {
        // "<mode> <object> <stage>\t<path>", a line for each stage of each conflicted path.
        return GitProcess.read(
                worktree,
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
    private static Either<GitFailure<ProcessError>, List<ExecutedCommand>> commitStaged(Path worktree) {
        // diff --cached --quiet exits with 1, saying nothing, when something is staged.
        return GitProcess.ask(worktree, GitCommand.reading("diff", "--cached", "--quiet"))
                .flatMap(nothingStaged -> nothingStaged.isDefined()
                        ? GitFailure.nothing()
                        : GitProcess.log(worktree, git("commit", "--quiet", "--no-edit")));
    }
}
