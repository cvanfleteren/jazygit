package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.i18n.Messages;

import java.nio.file.Path;

import static net.vanfleteren.jazygit.git.GitCommand.git;

/**
 * Checks out branches through the {@code git checkout} command line tool, so the working tree is
 * handled exactly like git itself does: local changes are carried over, and conflicting ones make
 * the checkout fail.
 */
public final class CliCheckout {

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
            // A trailing "--" keeps git from treating branch names as paths.
            return GitProcess.runOrThrow(workTree, git("checkout", "--quiet", branch, "--")).log();
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
        // Git succeeds without stashing when there is nothing to stash; then there is nothing to pop
        // either, and popping would apply an older, unrelated stash.
        long before = stashCount(workTree);
        List<String> executed = GitProcess.runOrThrow(workTree, git("stash", "push", "--quiet")).log();
        boolean stashed = stashCount(workTree) > before;
        try {
            executed = executed.appendAll(checkout(workTree, branch));
        } catch (GitCommandException e) {
            List<String> all = executed.appendAll(e.commands());
            if (stashed) {
                try {
                    all = all.appendAll(GitProcess.runOrThrow(workTree, git("stash", "pop", "--quiet")).log());
                } catch (GitCommandException popFailure) {
                    all = all.appendAll(popFailure.commands());
                }
            }
            throw new GitCommandException(e.getMessage(), all, e);
        }
        if (stashed) {
            try {
                executed = executed.appendAll(GitProcess.runOrThrow(workTree, git("stash", "pop", "--quiet")).log());
            } catch (GitCommandException e) {
                throw new GitCommandException(Messages.get("git.stashPopFailed", e.getMessage()),
                        executed.appendAll(e.commands()), e);
            }
        }
        return executed;
    }

    private static long stashCount(Path workTree) {
        return GitProcess.runOrThrow(workTree, git("stash", "list")).stdout().lines().count();
    }

    /**
     * Runs {@code git checkout -b <name> <startPoint>} in {@code workTree}: creates the branch at
     * {@code startPoint} and switches to it.
     *
     * @throws IllegalStateException if git cannot be run or refuses; the message then is git's own
     *                               explanation
     */
    public static List<String> createBranch(Path workTree, String name, String startPoint) {
        return GitProcess.runOrThrow(workTree, git("checkout", "--quiet", "-b", name, startPoint, "--")).log();
    }
}
