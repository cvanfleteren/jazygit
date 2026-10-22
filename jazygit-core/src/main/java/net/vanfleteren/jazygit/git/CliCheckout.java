package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.*;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Checks out branches through the {@code git checkout} command line tool, so the working tree is
 * handled exactly like git itself does: local changes are carried over, and conflicting ones make
 * the checkout fail.
 */
final class CliCheckout {

    // What git says when stashing the changes would let the checkout go through. It is matched in
    // English, so git is run with the C locale.
    private static final String LOCAL_CHANGES =
            "Your local changes to the following files would be overwritten by checkout";

    private CliCheckout() {}

    /**
     * Runs {@code git checkout <branch>} in {@code worktree}.
     *
     * @return the command lines that were run, or why git refused, {@link LocalChanges} when stashing
     * the uncommitted changes would let the checkout go through
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> checkout(Path worktree, Branch.Name branch) {
        // A trailing "--" keeps git from treating branch names as paths.
        return GitProcess.log(worktree, git("checkout", "--quiet", branch.value(), "--"))
                .mapLeft(failure -> failure.map(CliCheckout::classify));
    }

    static GitError classify(ProcessError error) {
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
    static Either<GitFailure<GitError>, List<ExecutedCommand>> checkoutWithStash(Path worktree, Branch.Name branch) {
        return GitFailure.<GitError, CliStash.Stashed>widen(CliStash.pushQuietly(worktree))
                .flatMap(stashed -> GitFailure.sequence(
                        () -> Either.right(stashed.commands()),
                        () -> GitFailure.<GitError, List<ExecutedCommand>>widen(checkout(worktree, branch))
                                .mapLeft(failure ->
                                        stashed.any() ? failure.followedBy(CliStash.popQuietly(worktree)) : failure),
                        () -> stashed.any() ? restore(worktree) : GitFailure.nothing()));
    }

    /**
     * Pops the changes that were stashed for the checkout.
     */
    private static Either<GitFailure<GitError>, List<ExecutedCommand>> restore(Path worktree) {
        return CliStash.popQuietly(worktree).mapLeft(failure -> failure.map(StashNotRestored::new));
    }

    /**
     * Runs {@code git checkout -b <name> <startPoint>} in {@code worktree}: creates the branch at
     * {@code startPoint} and switches to it; then records {@code parent} as the branch it came from, if given.
     * The parent is only a hint, so failing to record it does not fail the branch, which is there.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> checkoutNewBranch(
            Path worktree, Branch.Name name, String startPoint, Option<Branch.Name> parent) {
        return GitFailure.sequence(
                () -> GitProcess.log(worktree, git("checkout", "--quiet", "-b", name.value(), startPoint, "--")),
                () -> parent.map(p -> recordParent(worktree, name, p)).getOrElse(GitFailure::nothing));
    }

    /**
     * Records {@code parent} as the branch {@code name} came from; the command lines that were run, also when that
     * failed.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> recordParent(
            Path worktree, Branch.Name name, Branch.Name parent) {
        return Either.right(
                CliConfig.setParent(worktree, name, parent).fold(GitFailure::commands, commands -> commands));
    }

    /**
     * Runs {@code git checkout --track -b <name> <remote-tracking ref>} in {@code worktree}: creates the branch
     * at the remote-tracking branch of {@code upstream}, following it, and checks it out.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> checkoutNewTrackingBranch(
            Path worktree, Branch.Name name, RemoteBranch upstream) {
        return GitProcess.log(
                worktree,
                git(
                        "checkout",
                        "--quiet",
                        "--track",
                        "-b",
                        name.value(),
                        Refs.remote(upstream.remote(), upstream.branch()),
                        "--"));
    }
}
