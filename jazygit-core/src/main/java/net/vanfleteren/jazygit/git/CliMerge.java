package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
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
     * word the merge commit after the ref, so the message is given: the one {@code git fmt-merge-msg} writes for
     * the branch, as it does for {@code git merge <branch>}, so the user's {@code merge.*} config applies.
     */
    private static Either<GitFailure<GitError>, List<ExecutedCommand>> withMessage(
            Path worktree, BranchName branch, String fastForward) {
        return message(worktree, branch)
                .flatMap(message -> Unfinished.runOrStop(
                        worktree,
                        git("merge", fastForward, "--no-edit", "-m", message, new BranchRef.Local(branch).toArg())));
    }

    private static Either<GitFailure<GitError>, String> message(Path worktree, BranchName branch) {
        return GitProcess.read(
                        worktree,
                        GitCommand.reading("rev-parse", "--verify", new BranchRef.Local(branch).toArg()),
                        raw -> Either.right(raw.strip()))
                .<GitFailure<GitError>>mapLeft(error -> new GitFailure<>(error, List.empty()))
                .flatMap(sha -> TempFiles.using(
                        "jazygit-merge",
                        // What git merge hands to fmt-merge-msg for a local branch, as in FETCH_HEAD.
                        file -> Files.writeString(file, sha + "\t\tbranch '" + branch.value() + "' of .\n"),
                        failed -> new GitFailure<GitError>(failed, List.empty()),
                        file -> GitProcess.read(
                                        worktree,
                                        GitCommand.reading("fmt-merge-msg", "--file", file.toString()),
                                        raw -> Either.right(raw.strip()))
                                .mapLeft(error -> new GitFailure<GitError>(error, List.empty()))));
    }

    private static Either<GitFailure<GitError>, List<ExecutedCommand>> squash(Path worktree, BranchName branch) {
        return Unfinished.runOrStop(worktree, git("merge", "--squash", new BranchRef.Local(branch).toArg()));
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
