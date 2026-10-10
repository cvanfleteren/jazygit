package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.i18n.Messages;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * Changes the index (staging area) through the git command line tool.
 */
public final class CliIndex {

    private static final long TIMEOUT_SECONDS = 30;

    private CliIndex() {
    }

    /**
     * {@code git add -- <paths>}: stages the changes of the paths.
     */
    public static void add(Path workTree, List<String> paths) {
        run(workTree, List.of("add"), paths);
    }

    /**
     * {@code git rm --cached --force -- <paths>}: removes newly added paths from the index, leaving
     * them untracked in the working tree.
     */
    public static void removeCached(Path workTree, List<String> paths) {
        run(workTree, List.of("rm", "--cached", "--force", "--quiet"), paths);
    }

    /**
     * {@code git reset HEAD -- <paths>}: resets the index entries of the paths to HEAD.
     */
    public static void reset(Path workTree, List<String> paths) {
        run(workTree, List.of("reset", "--quiet", "HEAD"), paths);
    }

    /**
     * {@code git checkout -- <paths>}: resets the working tree of the paths to the index.
     */
    public static void checkoutFromIndex(Path workTree, List<String> paths) {
        run(workTree, List.of("checkout", "--quiet"), paths);
    }

    /**
     * {@code git checkout HEAD -- <paths>}: resets the index and the working tree of the paths to HEAD.
     */
    public static void checkoutFromHead(Path workTree, List<String> paths) {
        run(workTree, List.of("checkout", "--quiet", "HEAD"), paths);
    }

    /**
     * {@code git rm --force -- <paths>}: removes newly added paths from the index and the working tree.
     */
    public static void removeAdded(Path workTree, List<String> paths) {
        run(workTree, List.of("rm", "--force", "--quiet"), paths);
    }

    /**
     * Deletes untracked files and directories (like {@code rm -r}). Paths outside the working tree, and
     * the working tree itself, are refused.
     */
    public static void deleteUntracked(Path workTree, List<String> paths) {
        Path root = workTree.toAbsolutePath().normalize();
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
        }
    }

    /**
     * {@code git commit -m <summary> [-m <description>]}: commits the index.
     */
    public static void commit(Path workTree, String summary, String description) {
        List<String> command = new ArrayList<>(List.of("git", "commit", "--quiet", "-m", summary));
        if (!description.isBlank()) {
            command.addAll(List.of("-m", description));
        }
        execute(workTree, command, "commit");
    }

    /**
     * {@code git commit --amend --no-edit}: amends the last commit with the index.
     */
    public static void amend(Path workTree) {
        execute(workTree, List.of("git", "commit", "--quiet", "--amend", "--no-edit"), "commit");
    }

    /**
     * {@code git commit --allow-empty --amend --only -m <summary> [-m <description>]}: replaces the
     * message of the last commit, whatever is staged.
     */
    public static void reword(Path workTree, String summary, String description) {
        List<String> command = new ArrayList<>(
                List.of("git", "commit", "--quiet", "--allow-empty", "--amend", "--only", "-m", summary));
        if (!description.isBlank()) {
            command.addAll(List.of("-m", description));
        }
        execute(workTree, command, "commit");
    }

    private static void run(Path workTree, List<String> subcommand, List<String> paths) {
        if (paths.isEmpty()) {
            return;
        }
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(subcommand);
        command.add("--");
        command.addAll(paths);
        execute(workTree, command, subcommand.getFirst());
    }

    private static void execute(Path workTree, List<String> command, String name) {
        try {
            ProcessResult result = new ProcessExecutor()
                    .command(command)
                    .directory(workTree.toFile())
                    .readOutput(true)
                    .redirectErrorStream(true)
                    .exitValueAny()
                    .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .execute();
            if (result.getExitValue() != 0) {
                throw new IllegalStateException(
                        CliCheckout.explanation(result.outputUTF8(), result.getExitValue()));
            }
        } catch (IOException e) {
            throw new IllegalStateException(Messages.get("git.notInstalled"), e);
        } catch (TimeoutException e) {
            throw new IllegalStateException(Messages.get("git.timedOut", name, workTree), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(Messages.get("git.interrupted", name), e);
        }
    }
}
