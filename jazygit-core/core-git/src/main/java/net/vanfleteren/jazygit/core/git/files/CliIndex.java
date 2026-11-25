package net.vanfleteren.jazygit.core.git.files;

import static net.vanfleteren.jazygit.core.git.process.GitCommand.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.DiscardPlan;
import net.vanfleteren.jazygit.core.git.model.MarkedFile;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitError.RefusedPath;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.git.process.GitProcess;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;

/**
 * The {@link Index} of a repository, the staging area, changed through the git command line tool.
 */
public final class CliIndex implements Index {

    private final Path worktree;

    public CliIndex(Path worktree) {
        this.worktree = worktree;
    }

    /**
     * {@code git add -- <paths>}: stages the changes of the paths.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> stage(List<String> paths) {
        return onPaths(worktree, List.of("add"), paths);
    }

    /**
     * {@code git rm --cached --force -- <paths>}: removes newly added paths from the index, leaving
     * them untracked in the working tree.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> unstageNew(List<String> paths) {
        return onPaths(worktree, List.of("rm", "--cached", "--force", "--quiet"), paths);
    }

    /**
     * {@code git reset HEAD -- <paths>}: resets the index entries of the paths to HEAD.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> unstage(List<String> paths) {
        return onPaths(worktree, List.of("reset", "--quiet", "HEAD"), paths);
    }

    /**
     * {@code git checkout -- <paths>}: resets the working tree of the paths to the index.
     */
    static Either<GitFailure, List<ExecutedCommand>> checkoutFromIndex(Path worktree, List<String> paths) {
        return onPaths(worktree, List.of("checkout", "--quiet"), paths);
    }

    /**
     * {@code git checkout HEAD -- <paths>}: resets the index and the working tree of the paths to HEAD.
     */
    static Either<GitFailure, List<ExecutedCommand>> checkoutFromHead(Path worktree, List<String> paths) {
        return onPaths(worktree, List.of("checkout", "--quiet", "HEAD"), paths);
    }

    /**
     * {@code git rm --force -- <paths>}: removes newly added paths from the index and the working tree.
     */
    static Either<GitFailure, List<ExecutedCommand>> removeAdded(Path worktree, List<String> paths) {
        return onPaths(worktree, List.of("rm", "--force", "--quiet"), paths);
    }

    /**
     * The commands of {@link Conflict#keeping}, each on {@code -- <path>}: {@code git checkout --ours|--theirs}, then
     * {@code git add}; or {@code git rm --quiet} when that side deleted the file.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> keep(String path, Conflict conflict, Conflict.Version version) {
        return GitFailure.sequence(
                conflict.keeping(version).map(arguments -> () -> onPaths(worktree, arguments, List.of(path))));
    }

    /**
     * {@code git checkout --merge -- <path>}: the conflict again, from what the index keeps of it once resolved.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> restoreConflict(String path) {
        return onPaths(worktree, List.of("checkout", "--merge"), List.of(path));
    }

    /**
     * Reads each file for blocks of conflict markers; a file that cannot be read, as one that is not there, has none.
     */
    @Override
    public Either<GitError, List<MarkedFile>> markers(List<String> paths) {
        Path root = worktree.toAbsolutePath().normalize();
        return Either.right(paths.flatMap(path -> inside(root, path)
                .toOption()
                .flatMap(file -> Try.of(() -> Files.readString(file)).toOption())
                .map(MarkedFile::blockStarts)
                .filter(lines -> !lines.isEmpty())
                .map(lines -> new MarkedFile(path, lines))));
    }

    /**
     * Throws changes away as {@code plan} says.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> discard(DiscardPlan plan) {
        return GitFailure.sequence(
                () -> removeAdded(worktree, plan.added()),
                () -> checkoutFromHead(worktree, plan.fromHead()),
                () -> checkoutFromIndex(worktree, plan.fromIndex()),
                () -> deleteUntracked(worktree, plan.untracked()));
    }

    /**
     * {@code git clean --force -d --quiet -- <paths>}: deletes untracked files, and untracked directories with
     * what is untracked in them. Git leaves ignored files alone, and a repository nested in the working tree.
     * Paths outside the working tree, and the working tree itself, are refused before git runs.
     */
    static Either<GitFailure, List<ExecutedCommand>> deleteUntracked(Path worktree, List<String> paths) {
        Path root = worktree.toAbsolutePath().normalize();
        return paths.map(path -> inside(root, path))
                .find(Either::isLeft)
                .<Either<GitFailure, List<ExecutedCommand>>>map(
                        refused -> Either.left(new GitFailure(refused.getLeft(), List.empty())))
                .getOrElse(() -> onPaths(worktree, List.of("clean", "--force", "-d", "--quiet"), paths));
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
    private static Either<GitFailure, List<ExecutedCommand>> onPaths(
            Path worktree, List<String> subcommand, List<String> paths) {
        return paths.isEmpty()
                ? GitFailure.nothing()
                : GitProcess.log(
                        worktree, git(subcommand.append("--").appendAll(paths)).withLiteralPaths());
    }
}
