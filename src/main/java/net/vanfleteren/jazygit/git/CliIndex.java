package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.DeleteFailed;
import net.vanfleteren.jazygit.git.GitError.DiscardError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.RefusedPath;
import net.vanfleteren.jazygit.git.GitProcess.Output;
import net.vanfleteren.jazygit.git.model.CommitMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

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
    public static Either<GitFailure<ProcessError>, List<String>> add(Path workTree, List<String> paths) {
        return onPaths(workTree, List.of("add"), paths);
    }

    /**
     * {@code git rm --cached --force -- <paths>}: removes newly added paths from the index, leaving
     * them untracked in the working tree.
     */
    public static Either<GitFailure<ProcessError>, List<String>> removeCached(Path workTree, List<String> paths) {
        return onPaths(workTree, List.of("rm", "--cached", "--force", "--quiet"), paths);
    }

    /**
     * {@code git reset HEAD -- <paths>}: resets the index entries of the paths to HEAD.
     */
    public static Either<GitFailure<ProcessError>, List<String>> reset(Path workTree, List<String> paths) {
        return onPaths(workTree, List.of("reset", "--quiet", "HEAD"), paths);
    }

    /**
     * {@code git checkout -- <paths>}: resets the working tree of the paths to the index.
     */
    public static Either<GitFailure<ProcessError>, List<String>> checkoutFromIndex(Path workTree, List<String> paths) {
        return onPaths(workTree, List.of("checkout", "--quiet"), paths);
    }

    /**
     * {@code git checkout HEAD -- <paths>}: resets the index and the working tree of the paths to HEAD.
     */
    public static Either<GitFailure<ProcessError>, List<String>> checkoutFromHead(Path workTree, List<String> paths) {
        return onPaths(workTree, List.of("checkout", "--quiet", "HEAD"), paths);
    }

    /**
     * {@code git rm --force -- <paths>}: removes newly added paths from the index and the working tree.
     */
    public static Either<GitFailure<ProcessError>, List<String>> removeAdded(Path workTree, List<String> paths) {
        return onPaths(workTree, List.of("rm", "--force", "--quiet"), paths);
    }

    /**
     * Deletes untracked files and directories (like {@code rm -r}). Paths outside the working tree, and
     * the working tree itself, are refused.
     */
    public static Either<GitFailure<DiscardError>, List<String>> deleteUntracked(Path workTree, List<String> paths) {
        Path root = workTree.toAbsolutePath().normalize();
        return GitFailure.sequence(paths.map(path -> () -> inside(root, path)
                .flatMap(target -> deleteTree(target, path))
                .map(deleted -> List.of(CommandLine.format(List.of("rm", "-r", "--", path))))
                .mapLeft(error -> new GitFailure<>(error, List.empty()))));
    }

    /**
     * {@code path} resolved against the working tree {@code root}, if it is strictly inside it and not
     * inside {@code .git}.
     */
    static Either<DiscardError, Path> inside(Path root, String path) {
        Path target = root.resolve(path).normalize();
        return target.equals(root) || !target.startsWith(root) || target.startsWith(root.resolve(".git"))
                ? Either.left(new RefusedPath(path))
                : Either.right(target);
    }

    private static Either<DiscardError, Path> deleteTree(Path target, String path) {
        try (Stream<Path> tree = Files.walk(target)) {
            // Children before their directory.
            for (Path p : tree.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
            return Either.right(target);
        } catch (NoSuchFileException e) {
            // Already gone.
            return Either.right(target);
        } catch (IOException e) {
            return Either.left(new DeleteFailed(path, String.valueOf(e.getMessage())));
        }
    }

    /**
     * {@code git commit -m <summary> [-m <description>]}: commits the index.
     */
    public static Either<GitFailure<ProcessError>, List<String>> commit(Path workTree, CommitMessage message) {
        return run(workTree, List.of("commit", "--quiet").appendAll(messageArgs(message)));
    }

    /**
     * {@code git commit --amend --no-edit}: amends the last commit with the index.
     */
    public static Either<GitFailure<ProcessError>, List<String>> amend(Path workTree) {
        return run(workTree, List.of("commit", "--quiet", "--amend", "--no-edit"));
    }

    /**
     * {@code git commit --allow-empty --amend --only -m <summary> [-m <description>]}: replaces the
     * message of the last commit, whatever is staged.
     */
    public static Either<GitFailure<ProcessError>, List<String>> reword(Path workTree, CommitMessage message) {
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
    private static Either<GitFailure<ProcessError>, List<String>> onPaths(Path workTree, List<String> subcommand,
                                                                        List<String> paths) {
        return paths.isEmpty() ? Either.right(List.empty()) : run(workTree, subcommand.append("--").appendAll(paths));
    }

    private static Either<GitFailure<ProcessError>, List<String>> run(Path workTree, List<String> args) {
        return GitProcess.run(workTree, git(args)).map(Output::log);
    }
}
