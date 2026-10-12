package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.i18n.Messages;

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
    public static List<String> add(Path workTree, List<String> paths) {
        return run(workTree, List.of("add"), paths);
    }

    /**
     * {@code git rm --cached --force -- <paths>}: removes newly added paths from the index, leaving
     * them untracked in the working tree.
     */
    public static List<String> removeCached(Path workTree, List<String> paths) {
        return run(workTree, List.of("rm", "--cached", "--force", "--quiet"), paths);
    }

    /**
     * {@code git reset HEAD -- <paths>}: resets the index entries of the paths to HEAD.
     */
    public static List<String> reset(Path workTree, List<String> paths) {
        return run(workTree, List.of("reset", "--quiet", "HEAD"), paths);
    }

    /**
     * {@code git checkout -- <paths>}: resets the working tree of the paths to the index.
     */
    public static List<String> checkoutFromIndex(Path workTree, List<String> paths) {
        return run(workTree, List.of("checkout", "--quiet"), paths);
    }

    /**
     * {@code git checkout HEAD -- <paths>}: resets the index and the working tree of the paths to HEAD.
     */
    public static List<String> checkoutFromHead(Path workTree, List<String> paths) {
        return run(workTree, List.of("checkout", "--quiet", "HEAD"), paths);
    }

    /**
     * {@code git rm --force -- <paths>}: removes newly added paths from the index and the working tree.
     */
    public static List<String> removeAdded(Path workTree, List<String> paths) {
        return run(workTree, List.of("rm", "--force", "--quiet"), paths);
    }

    /**
     * Deletes untracked files and directories (like {@code rm -r}). Paths outside the working tree, and
     * the working tree itself, are refused.
     */
    public static List<String> deleteUntracked(Path workTree, List<String> paths) {
        Path root = workTree.toAbsolutePath().normalize();
        List<String> executed = List.empty();
        for (String path : paths) {
            Path target = root.resolve(path).normalize();
            if (target.equals(root) || !target.startsWith(root) || target.startsWith(root.resolve(".git"))) {
                throw new IllegalStateException(Messages.get("git.refusedPath", path));
            }
            try (Stream<Path> tree = Files.walk(target)) {
                // Children before their directory.
                for (Path p : tree.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
            } catch (NoSuchFileException e) {
                // Already gone.
            } catch (IOException e) {
                throw new IllegalStateException(Messages.get("git.deleteFailed", path, e.getMessage()), e);
            }
            executed = executed.append(CommandLine.format(List.of("rm", "-r", "--", path)));
        }
        return executed;
    }

    /**
     * {@code git commit -m <summary> [-m <description>]}: commits the index.
     */
    public static List<String> commit(Path workTree, String summary, String description) {
        List<String> command = List.of("commit", "--quiet", "-m", summary);
        if (!description.isBlank()) {
            command = command.appendAll(List.of("-m", description));
        }
        return execute(workTree, command);
    }

    /**
     * {@code git commit --amend --no-edit}: amends the last commit with the index.
     */
    public static List<String> amend(Path workTree) {
        return execute(workTree, List.of("commit", "--quiet", "--amend", "--no-edit"));
    }

    /**
     * {@code git commit --allow-empty --amend --only -m <summary> [-m <description>]}: replaces the
     * message of the last commit, whatever is staged.
     */
    public static List<String> reword(Path workTree, String summary, String description) {
        List<String> command =
                List.of("commit", "--quiet", "--allow-empty", "--amend", "--only", "-m", summary);
        if (!description.isBlank()) {
            command = command.appendAll(List.of("-m", description));
        }
        return execute(workTree, command);
    }

    private static List<String> run(Path workTree, List<String> subcommand, List<String> paths) {
        if (paths.isEmpty()) {
            return List.of();
        }
        return execute(workTree, subcommand.append("--").appendAll(paths));
    }

    private static List<String> execute(Path workTree, List<String> args) {
        return GitProcess.runOrThrow(workTree, git(args)).log();
    }
}
