package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.CheckoutError;
import net.vanfleteren.jazygit.git.GitError.CommandFailed;
import net.vanfleteren.jazygit.git.GitError.LocalChanges;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.StashCheckoutError;
import net.vanfleteren.jazygit.git.GitError.StashNotRestored;

import net.vanfleteren.jazygit.git.model.RemoteBranch;

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
     * @return the command lines that were run, or why git refused, {@link LocalChanges} when stashing
     *         the uncommitted changes would let the checkout go through
     */
    public static Either<GitFailure<CheckoutError>, List<ExecutedCommand>> checkout(Path workTree, Branch.Name branch) {
        // A trailing "--" keeps git from treating branch names as paths.
        return GitProcess.log(workTree, git("checkout", "--quiet", branch.value(), "--"))
                .mapLeft(failure -> failure.map(CliCheckout::classify));
    }

    static CheckoutError classify(ProcessError error) {
        return error instanceof CommandFailed failed && failed.says(LOCAL_CHANGES)
                ? new LocalChanges(failed.explanation().getOrElse(""))
                : error;
    }

    /**
     * Stashes the uncommitted changes, checks out {@code branch} and pops the stash again. If the
     * checkout fails the stash is popped back, so the changes are never left stashed by a failure of
     * this method itself; only a conflict while popping on the new branch leaves them in the stash,
     * which is a {@link StashNotRestored}.
     */
    public static Either<GitFailure<StashCheckoutError>, List<ExecutedCommand>> checkoutWithStash(Path workTree, Branch.Name branch) {
        return GitFailure.<StashCheckoutError, CliStash.Stashed>widen(CliStash.pushQuietly(workTree)).flatMap(stashed ->
                GitFailure.sequence(
                        () -> Either.right(stashed.commands()),
                        () -> GitFailure.<StashCheckoutError, List<ExecutedCommand>>widen(checkout(workTree, branch)).mapLeft(failure ->
                                stashed.any() ? failure.followedBy(CliStash.popQuietly(workTree)) : failure),
                        () -> stashed.any() ? restore(workTree) : GitFailure.nothing()));
    }

    /**
     * Pops the changes that were stashed for the checkout.
     */
    private static Either<GitFailure<StashCheckoutError>, List<ExecutedCommand>> restore(Path workTree) {
        return CliStash.popQuietly(workTree).mapLeft(failure -> failure.map(StashNotRestored::new));
    }

    /**
     * Runs {@code git checkout -b <name> <startPoint>} in {@code workTree}: creates the branch at
     * {@code startPoint} and switches to it.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> checkoutNewBranch(Path workTree, Branch.Name name,
                                                                            String startPoint) {
        return GitProcess.log(workTree, git("checkout", "--quiet", "-b", name.value(), startPoint, "--"));
    }

    /**
     * Runs {@code git checkout --track -b <name> <remote-tracking ref>} in {@code workTree}: creates the branch
     * at the remote-tracking branch of {@code upstream}, following it, and checks it out.
     */
    public static Either<GitFailure<ProcessError>, List<ExecutedCommand>> checkoutNewTrackingBranch(Path workTree, Branch.Name name,
                                                                                              RemoteBranch upstream) {
        return GitProcess.log(workTree, git("checkout", "--quiet", "--track", "-b", name.value(),
                Refs.remote(upstream.remote(), upstream.branch()), "--"));
    }
}
