package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.i18n.Messages;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
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
    public static List<String> checkout(Path workTree, String branch) {
        try {
            return List.of(exec(workTree, "git", "checkout", "--quiet", branch, "--").command());
        } catch (GitCommandException e) {
            if (e.getMessage().contains(LOCAL_CHANGES)) {
                throw new LocalChangesException(e.getMessage(), e.commands());
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
    public static List<String> checkoutWithStash(Path workTree, String branch) {
        List<String> executed = new ArrayList<>();
        // Git succeeds without stashing when there is nothing to stash; then there is nothing to pop
        // either, and popping would apply an older, unrelated stash.
        long before = stashCount(workTree);
        executed.add(exec(workTree, "git", "stash", "push", "--quiet").command());
        boolean stashed = stashCount(workTree) > before;
        try {
            executed.addAll(checkout(workTree, branch));
        } catch (GitCommandException e) {
            List<String> all = concat(executed, e.commands());
            if (stashed) {
                try {
                    all.add(exec(workTree, "git", "stash", "pop", "--quiet").command());
                } catch (GitCommandException popFailure) {
                    all.addAll(popFailure.commands());
                }
            }
            throw new GitCommandException(e.getMessage(), all, e);
        }
        if (stashed) {
            try {
                executed.add(exec(workTree, "git", "stash", "pop", "--quiet").command());
            } catch (GitCommandException e) {
                throw new GitCommandException(Messages.get("git.stashPopFailed", e.getMessage()),
                        concat(executed, e.commands()), e);
            }
        }
        return executed;
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
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
    public static List<String> createBranch(Path workTree, String name, String startPoint) {
        return List.of(exec(workTree, "git", "checkout", "--quiet", "-b", name, startPoint, "--").command());
    }

    /**
     * The output of a finished command, and the command line it was run as.
     */
    record Result(String output, String command) {
    }

    static String run(Path workTree, String... command) {
        return exec(workTree, command).output();
    }

    static Result exec(Path workTree, String... command) {
        List<String> line = List.of(CommandLine.format(List.of(command)));
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
                throw new GitCommandException(explanation(result.outputUTF8(), result.getExitValue()), line);
            }
            return new Result(result.outputUTF8().strip(), CommandLine.format(List.of(command)));
        } catch (IOException e) {
            throw new GitCommandException(Messages.get("git.notInstalled"), line, e);
        } catch (TimeoutException e) {
            throw new GitCommandException(Messages.get("git.timedOut", "checkout", workTree), line, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitCommandException(Messages.get("git.interrupted", "checkout"), line, e);
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
