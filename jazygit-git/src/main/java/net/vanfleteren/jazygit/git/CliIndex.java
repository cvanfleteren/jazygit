package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.RefusedPath;
import net.vanfleteren.jazygit.git.model.DiscardPlan;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Changes the index (staging area) through the git command line tool.
 */
final class CliIndex {

    private CliIndex() {}

    /**
     * {@code git add -- <paths>}: stages the changes of the paths.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> add(Path worktree, List<String> paths) {
        return onPaths(worktree, List.of("add"), paths);
    }

    /**
     * {@code git rm --cached --force -- <paths>}: removes newly added paths from the index, leaving
     * them untracked in the working tree.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> removeCached(Path worktree, List<String> paths) {
        return onPaths(worktree, List.of("rm", "--cached", "--force", "--quiet"), paths);
    }

    /**
     * {@code git reset HEAD -- <paths>}: resets the index entries of the paths to HEAD.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> reset(Path worktree, List<String> paths) {
        return onPaths(worktree, List.of("reset", "--quiet", "HEAD"), paths);
    }

    /**
     * {@code git checkout -- <paths>}: resets the working tree of the paths to the index.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> checkoutFromIndex(
            Path worktree, List<String> paths) {
        return onPaths(worktree, List.of("checkout", "--quiet"), paths);
    }

    /**
     * {@code git checkout HEAD -- <paths>}: resets the index and the working tree of the paths to HEAD.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> checkoutFromHead(Path worktree, List<String> paths) {
        return onPaths(worktree, List.of("checkout", "--quiet", "HEAD"), paths);
    }

    /**
     * {@code git rm --force -- <paths>}: removes newly added paths from the index and the working tree.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> removeAdded(Path worktree, List<String> paths) {
        return onPaths(worktree, List.of("rm", "--force", "--quiet"), paths);
    }

    /**
     * Throws changes away as {@code plan} says.
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> discard(Path worktree, DiscardPlan plan) {
        return GitFailure.sequence(
                () -> GitFailure.widen(removeAdded(worktree, plan.added())),
                () -> GitFailure.widen(checkoutFromHead(worktree, plan.fromHead())),
                () -> GitFailure.widen(checkoutFromIndex(worktree, plan.fromIndex())),
                () -> GitFailure.widen(deleteUntracked(worktree, plan.untracked())));
    }

    /**
     * {@code git clean --force -d --quiet -- <paths>}: deletes untracked files, and untracked directories with
     * what is untracked in them. Git leaves ignored files alone, and a repository nested in the working tree.
     * Paths outside the working tree, and the working tree itself, are refused before git runs.
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> deleteUntracked(Path worktree, List<String> paths) {
        Path root = worktree.toAbsolutePath().normalize();
        return paths.map(path -> inside(root, path))
                .find(Either::isLeft)
                .<Either<GitFailure<GitError>, List<ExecutedCommand>>>map(
                        refused -> Either.left(new GitFailure<>(refused.getLeft(), List.empty())))
                .getOrElse(
                        () -> GitFailure.widen(onPaths(worktree, List.of("clean", "--force", "-d", "--quiet"), paths)));
    }

    /**
     * {@code path} resolved against the working tree {@code root}, if it is strictly inside it and not
     * inside {@code .git}.
     */
    static Either<RefusedPath, Path> inside(Path root, String path) {
        Path target = root.resolve(path).normalize();
        return target.equals(root) || !target.startsWith(root) || target.startsWith(root.resolve(".git"))
                ? Either.left(new RefusedPath(path))
                : Either.right(target);
    }

    /**
     * Runs {@code subcommand -- paths}; nothing when there are no paths.
     */
    private static Either<GitFailure<ProcessError>, List<ExecutedCommand>> onPaths(
            Path worktree, List<String> subcommand, List<String> paths) {
        return paths.isEmpty()
                ? GitFailure.nothing()
                : GitProcess.log(
                        worktree, git(subcommand.append("--").appendAll(paths)).withLiteralPaths());
    }
}
