package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.i18n.Messages;
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
public final class CliCheckout {

    private static final long TIMEOUT_SECONDS = 30;
    // What git says when stashing the changes would let the checkout go through. It is matched in
    // English, so git is run with the C locale.
    private static final String LOCAL_CHANGES =
            "Your local changes to the following files would be overwritten by checkout";

    private CliCheckout() {
    }

    /**
     * Runs {@code git checkout <branch>} in {@code workTree}.
     *
     * @throws LocalChangesException if git refuses because uncommitted changes would be overwritten
     * @throws IllegalStateException  if git cannot be run or refuses the checkout; the message then
     *                                is git's own explanation
     */
    public static void checkout(Path workTree, String branch) {
        try {
            run(workTree, "git", "checkout", "--quiet", branch, "--");
        } catch (IllegalStateException e) {
            if (e.getMessage().contains(LOCAL_CHANGES)) {
                throw new LocalChangesException(e.getMessage());
            }
            throw e;
        }
    }

    /**
     * Stashes the uncommitted changes, checks out {@code branch} and pops the stash again. If the
     * checkout fails the stash is popped back, so the changes are never left stashed by a failure of
     * this method itself; only a conflict while popping on the new branch leaves them in the stash.
     *
     * @throws IllegalStateException if git cannot be run or refuses; the message then is git's own
     *                               explanation
     */
    public static void checkoutWithStash(Path workTree, String branch) {
        // Git succeeds without stashing when there is nothing to stash; then there is nothing to pop
        // either, and popping would apply an older, unrelated stash.
        long before = stashCount(workTree);
        run(workTree, "git", "stash", "push", "--quiet");
        boolean stashed = stashCount(workTree) > before;
        try {
            checkout(workTree, branch);
        } catch (RuntimeException e) {
            if (stashed) {
                run(workTree, "git", "stash", "pop", "--quiet");
            }
            throw e;
        }
        if (stashed) {
            try {
                run(workTree, "git", "stash", "pop", "--quiet");
            } catch (IllegalStateException e) {
                throw new IllegalStateException(Messages.get("git.stashPopFailed", e.getMessage()), e);
            }
        }
    }

    private static long stashCount(Path workTree) {
        return run(workTree, "git", "stash", "list").lines().count();
    }

    /**
     * Runs {@code git checkout -b <name> <startPoint>} in {@code workTree}: creates the branch at
     * {@code startPoint} and switches to it.
     *
     * @throws IllegalStateException if git cannot be run or refuses; the message then is git's own
     *                               explanation
     */
    public static void createBranch(Path workTree, String name, String startPoint) {
        run(workTree, "git", "checkout", "--quiet", "-b", name, startPoint, "--");
    }

    static String run(Path workTree, String... command) {
        try {
            ProcessResult result = new ProcessExecutor()
                    // A trailing "--" keeps git from treating branch names as paths.
                    .command(command)
                    .directory(workTree.toFile())
                    // Fail instead of waiting for credentials that cannot be typed.
                    .environment("GIT_TERMINAL_PROMPT", "0")
                    .environment("LC_ALL", "C")
                    .readOutput(true)
                    .redirectErrorStream(true)
                    .exitValueAny()
                    .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .execute();
            if (result.getExitValue() != 0) {
                throw new IllegalStateException(explanation(result.outputUTF8(), result.getExitValue()));
            }
            return result.outputUTF8().strip();
        } catch (IOException e) {
            throw new IllegalStateException(Messages.get("git.notInstalled"), e);
        } catch (TimeoutException e) {
            throw new IllegalStateException(Messages.get("git.timedOut", "checkout", workTree), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(Messages.get("git.interrupted", "checkout"), e);
        }
    }

    /**
     * The first line git printed, which states why it failed, e.g. {@code error: Your local
     * changes to the following files would be overwritten by checkout:}.
     */
    static String explanation(String output, int exitValue) {
        List<String> lines = output.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        return lines.isEmpty() ? Messages.get("git.exitedWithCode", String.valueOf(exitValue)) : lines.getFirst();
    }
}
