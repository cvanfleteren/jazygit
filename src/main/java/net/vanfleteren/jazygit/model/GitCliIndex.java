package net.vanfleteren.jazygit.model;

import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Changes the index (staging area) through the git command line tool.
 */
public final class GitCliIndex {

    private static final long TIMEOUT_SECONDS = 30;

    private GitCliIndex() {
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
                        GitCliCheckout.explanation(result.outputUTF8(), result.getExitValue()));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not run git; is it installed and on the PATH?", e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("git " + name + " timed out in " + workTree, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running git " + name, e);
        }
    }
}
