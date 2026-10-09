package net.vanfleteren.jazygit.model;

import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Checks out branches through the {@code git checkout} command line tool, so the working tree is
 * handled exactly like git itself does: local changes are carried over, and conflicting ones make
 * the checkout fail.
 */
public final class GitCliCheckout {

    private static final long TIMEOUT_SECONDS = 30;

    private GitCliCheckout() {
    }

    /**
     * Runs {@code git checkout <branch>} in {@code workTree}.
     *
     * @throws IllegalStateException if git cannot be run or refuses the checkout; the message then
     *                               is git's own explanation
     */
    public static void checkout(Path workTree, String branch) {
        try {
            ProcessResult result = new ProcessExecutor()
                    // The trailing "--" keeps git from treating the branch name as a path.
                    .command("git", "checkout", "--quiet", branch, "--")
                    .directory(workTree.toFile())
                    .readOutput(true)
                    .redirectErrorStream(true)
                    .exitValueAny()
                    .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .execute();
            if (result.getExitValue() != 0) {
                throw new IllegalStateException(explanation(result.outputUTF8(), result.getExitValue()));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not run git; is it installed and on the PATH?", e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("git checkout timed out in " + workTree, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running git checkout", e);
        }
    }

    /**
     * The first line git printed, which states why it failed, e.g. {@code error: Your local
     * changes to the following files would be overwritten by checkout:}.
     */
    static String explanation(String output, int exitValue) {
        List<String> lines = output.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        return lines.isEmpty() ? "git checkout exited with code " + exitValue : lines.getFirst();
    }
}
