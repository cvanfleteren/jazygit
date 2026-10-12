package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.CheckoutError;
import net.vanfleteren.jazygit.git.GitError.CommandFailed;
import net.vanfleteren.jazygit.git.GitError.LocalChanges;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.StashCheckoutError;
import net.vanfleteren.jazygit.git.GitError.StashNotRestored;
import net.vanfleteren.jazygit.git.GitProcess.Output;

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
    private static final GitCommand STASH_POP = git("stash", "pop", "--quiet");

    private CliCheckout() {
    }

    /**
     * Runs {@code git checkout <branch>} in {@code workTree}.
     *
     * @return the command lines that were run, or why git refused, {@link LocalChanges} when stashing
     *         the uncommitted changes would let the checkout go through
     */
    public static Either<GitFailure<CheckoutError>, List<String>> checkout(Path workTree, String branch) {
        // A trailing "--" keeps git from treating branch names as paths.
        return GitProcess.run(workTree, git("checkout", "--quiet", branch, "--"))
                .map(Output::log)
                .mapLeft(failure -> failure.map(CliCheckout::classify));
    }

    static CheckoutError classify(ProcessError error) {
        return error instanceof CommandFailed(String explanation, int ignored) && explanation.contains(LOCAL_CHANGES)
                ? new LocalChanges(explanation)
                : error;
    }

    /**
     * Stashes the uncommitted changes, checks out {@code branch} and pops the stash again. If the
     * checkout fails the stash is popped back, so the changes are never left stashed by a failure of
     * this method itself; only a conflict while popping on the new branch leaves them in the stash,
     * which is a {@link StashNotRestored}.
     */
    public static Either<GitFailure<StashCheckoutError>, List<String>> checkoutWithStash(Path workTree, String branch) {
        return GitFailure.<StashCheckoutError, Stashed>widen(stash(workTree)).flatMap(stashed ->
                checkout(workTree, branch).fold(
                        failure -> Either.left(GitFailure.<StashCheckoutError>widen(failure)
                                .after(stashed.commands())
                                .followedBy(stashed.any() ? GitProcess.attempt(workTree, STASH_POP) : List.empty())),
                        checkedOut -> restore(workTree, stashed, stashed.commands().appendAll(checkedOut))));
    }

    /**
     * Whether {@code git stash push} stashed anything, and the commands that found out.
     */
    private record Stashed(boolean any, List<String> commands) {
    }

    private static Either<GitFailure<ProcessError>, Stashed> stash(Path workTree) {
        // Git succeeds without stashing when there is nothing to stash; then there is nothing to pop
        // either, and popping would apply an older, unrelated stash.
        return stashCount(workTree).flatMap(before ->
                GitProcess.run(workTree, git("stash", "push", "--quiet")).flatMap(push ->
                        stashCount(workTree).map(after -> new Stashed(after > before, push.log()))));
    }

    private static Either<GitFailure<StashCheckoutError>, List<String>> restore(Path workTree, Stashed stashed,
                                                                              List<String> executed) {
        return stashed.any()
                ? GitProcess.run(workTree, STASH_POP)
                .map(popped -> executed.appendAll(popped.log()))
                .mapLeft(failure -> failure.after(executed).map(StashNotRestored::new))
                : Either.right(executed);
    }

    private static Either<GitFailure<ProcessError>, Long> stashCount(Path workTree) {
        return GitProcess.run(workTree, git("stash", "list")).map(output -> output.stdout().lines().count());
    }

    /**
     * Runs {@code git checkout -b <name> <startPoint>} in {@code workTree}: creates the branch at
     * {@code startPoint} and switches to it.
     */
    public static Either<GitFailure<ProcessError>, List<String>> createBranch(Path workTree, String name,
                                                                            String startPoint) {
        return GitProcess.run(workTree, git("checkout", "--quiet", "-b", name, startPoint, "--")).map(Output::log);
    }
}
