package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Try;
import net.vanfleteren.jazygit.git.GitError.DeleteFailed;
import net.vanfleteren.jazygit.git.GitError.DeleteUntrackedError;
import net.vanfleteren.jazygit.git.GitError.DiscardError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.RefusedPath;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.DiscardPlan;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Comparator;

import static net.vanfleteren.jazygit.git.GitCommand.git;

/**
 * Changes the index (staging area) through the git command line tool.
 */
public final class CliIndex {

    private CliIndex() {
    }

    /**
     * {@code git add -- <paths>}: stages the changes of the paths.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> add(Path workTree, List<String> paths) {
        return onPaths(workTree, List.of("add"), paths);
    }

    /**
     * {@code git rm --cached --force -- <paths>}: removes newly added paths from the index, leaving
     * them untracked in the working tree.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> removeCached(Path workTree, List<String> paths) {
        return onPaths(workTree, List.of("rm", "--cached", "--force", "--quiet"), paths);
    }

    /**
     * {@code git reset HEAD -- <paths>}: resets the index entries of the paths to HEAD.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> reset(Path workTree, List<String> paths) {
        return onPaths(workTree, List.of("reset", "--quiet", "HEAD"), paths);
    }

    /**
     * {@code git checkout -- <paths>}: resets the working tree of the paths to the index.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> checkoutFromIndex(Path workTree, List<String> paths) {
        return onPaths(workTree, List.of("checkout", "--quiet"), paths);
    }

    /**
     * {@code git checkout HEAD -- <paths>}: resets the index and the working tree of the paths to HEAD.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> checkoutFromHead(Path workTree, List<String> paths) {
        return onPaths(workTree, List.of("checkout", "--quiet", "HEAD"), paths);
    }

    /**
     * {@code git rm --force -- <paths>}: removes newly added paths from the index and the working tree.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> removeAdded(Path workTree, List<String> paths) {
        return onPaths(workTree, List.of("rm", "--force", "--quiet"), paths);
    }

    /**
     * Throws changes away as {@code plan} says.
     */
    public static Either<GitFailure<DiscardError>, List<ExecutedCommand>> discard(Path workTree, DiscardPlan plan) {
        return GitFailure.<DiscardError>sequence(
                () -> GitFailure.widen(removeAdded(workTree, plan.added())),
                () -> GitFailure.widen(checkoutFromHead(workTree, plan.fromHead())),
                () -> GitFailure.widen(checkoutFromIndex(workTree, plan.fromIndex())),
                () -> GitFailure.widen(deleteUntracked(workTree, plan.untracked())));
    }

    /**
     * Deletes untracked files and directories (like {@code rm -r}). Paths outside the working tree, and
     * the working tree itself, are refused.
     */
    public static Either<GitFailure<DeleteUntrackedError>, List<ExecutedCommand>> deleteUntracked(Path workTree,
                                                                                       List<String> paths) {
        Path root = workTree.toAbsolutePath().normalize();
        return GitFailure.sequence(paths.map(path -> () -> deleteUntracked(root, path,
                List.of(ExecutedCommand.of("rm", "-r", "--", path)))));
    }

    /**
     * Deletes the untracked {@code path}, logging it as {@code log}.
     */
    private static Either<GitFailure<DeleteUntrackedError>, List<ExecutedCommand>> deleteUntracked(Path root, String path,
                                                                                        List<ExecutedCommand> log) {
        return Either.<DeleteUntrackedError, Path>narrow(inside(root, path))
                .flatMap(target -> deleteTree(target, path))
                .map(deleted -> log)
                .mapLeft(error -> new GitFailure<>(error, log));
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

    private static Either<DeleteUntrackedError, Path> deleteTree(Path target, String path) {
        return Try.withResources(() -> Files.walk(target))
                // Children before their directory.
                .of(tree -> List.ofAll(tree.sorted(Comparator.reverseOrder())::iterator))
                .flatMap(files -> Try.traverse(files, file -> Try.of(() -> Files.deleteIfExists(file))))
                .map(deleted -> target)
                .recover(NoSuchFileException.class, target) // already gone
                .toEither()
                .mapLeft(e -> new DeleteFailed(path, e));
    }

    /**
     * {@code git commit -m <summary> [-m <description>]}: commits the index.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> commit(Path workTree, CommitMessage message) {
        return run(workTree, List.of("commit", "--quiet").appendAll(messageArgs(message)));
    }

    /**
     * {@code git commit --amend --no-edit}: amends the last commit with the index.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> amend(Path workTree) {
        return run(workTree, List.of("commit", "--quiet", "--amend", "--no-edit"));
    }

    /**
     * {@code git commit --allow-empty --amend --only -m <summary> [-m <description>]}: replaces the
     * message of the last commit, whatever is staged.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> reword(Path workTree, CommitMessage message) {
        return run(workTree, List.of("commit", "--quiet", "--allow-empty", "--amend", "--only")
                .appendAll(messageArgs(message)));
    }

    private static List<String> messageArgs(CommitMessage message) {
        return message.hasDescription()
                ? List.of("-m", message.summary(), "-m", message.description())
                : List.of("-m", message.summary());
    }

    /**
     * Runs {@code subcommand -- paths}; nothing when there are no paths.
     */
    private static Either<GitFailure<ProcessError>, List<ExecutedCommand>> onPaths(Path workTree, List<String> subcommand,
                                                                        List<String> paths) {
        return paths.isEmpty() ? GitFailure.nothing() : run(workTree, subcommand.append("--").appendAll(paths));
    }

    private static Either<GitFailure<ProcessError>, List<ExecutedCommand>> run(Path workTree, List<String> args) {
        return GitProcess.log(workTree, git(args));
    }
}
