package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.*;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * The {@link Checkout} of a repository, through the {@code git checkout} command line tool, so the working tree is
 * handled exactly like git itself does: local changes are carried over, and conflicting ones make the checkout fail.
 * Moving commits checks out through {@link #checkout} and {@link #checkoutNewBranch}, adding a worktree records its
 * parent through {@link #recordParent}.
 */
final class CliCheckout implements Checkout {

    // What git says when stashing the changes would let the checkout go through. It is matched in
    // English, so git is run with the C locale.
    private static final String LOCAL_CHANGES =
            "Your local changes to the following files would be overwritten by checkout";

    private final Path worktree;

    CliCheckout(Path worktree) {
        this.worktree = worktree;
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> branch(BranchName branch) {
        return checkout(worktree, branch);
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> newBranch(
            BranchName name, Revision startPoint, Option<BranchName> parent) {
        return checkoutNewBranch(worktree, name, startPoint, parent);
    }

    /**
     * Runs {@code git checkout <branch>} in {@code worktree}.
     *
     * @return the command lines that were run, or why git refused, {@link LocalChanges} when stashing
     * the uncommitted changes would let the checkout go through
     */
    static Either<GitFailure, List<ExecutedCommand>> checkout(Path worktree, BranchName branch) {
        // A trailing "--" keeps git from treating branch names as paths.
        return GitProcess.log(worktree, git("checkout", "--quiet", GitArgs.name(branch), "--"))
                .mapLeft(failure -> failure.map(CliCheckout::classify));
    }

    private static GitError classify(GitError error) {
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
    @Override
    public Either<GitFailure, List<ExecutedCommand>> branchWithStash(BranchName branch) {
        return CliStashes.pushQuietly(worktree)
                .flatMap(stashed -> GitFailure.sequence(
                        () -> Either.right(stashed.commands()),
                        () -> checkout(worktree, branch)
                                .mapLeft(failure ->
                                        stashed.any() ? failure.followedBy(CliStashes.popQuietly(worktree)) : failure),
                        () -> stashed.any() ? restore() : GitFailure.nothing()));
    }

    /**
     * Pops the changes that were stashed for the checkout.
     */
    private Either<GitFailure, List<ExecutedCommand>> restore() {
        return CliStashes.popQuietly(worktree).mapLeft(failure -> failure.map(StashNotRestored::new));
    }

    /**
     * Runs {@code git checkout -b <name> <startPoint>} in {@code worktree}: creates the branch at
     * {@code startPoint} and switches to it; then records {@code parent} as the branch it came from, if given.
     * The parent is only a hint, so failing to record it does not fail the branch, which is there. Whether the
     * branch follows {@code startPoint} is up to git's {@code branch.autoSetupMerge} (ADR 40).
     */
    static Either<GitFailure, List<ExecutedCommand>> checkoutNewBranch(
            Path worktree, BranchName name, Revision startPoint, Option<BranchName> parent) {
        return GitFailure.sequence(
                () -> GitProcess.log(
                        worktree,
                        git("checkout", "--quiet", "-b", GitArgs.name(name), GitArgs.revision(startPoint), "--")),
                () -> parent.map(p -> recordParent(worktree, name, p)).getOrElse(GitFailure::nothing));
    }

    /**
     * Records {@code parent} as the branch {@code name} came from; the command lines that were run, also when that
     * failed.
     */
    static Either<GitFailure, List<ExecutedCommand>> recordParent(Path worktree, BranchName name, BranchName parent) {
        return Either.right(
                CliBranches.setParent(worktree, name, parent).fold(GitFailure::commands, commands -> commands));
    }

    /**
     * Runs {@code git checkout --track -b <name> <remote-tracking ref>}: creates the branch at the
     * remote-tracking branch of {@code upstream}, following it, and checks it out.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> newTrackingBranch(BranchName name, RemoteBranch upstream) {
        return GitProcess.log(
                worktree,
                git(
                        "checkout",
                        "--quiet",
                        "--track",
                        "-b",
                        GitArgs.name(name),
                        GitArgs.revision(upstream.tracking()),
                        "--"));
    }

    @Override
    public Either<GitFailure, Option<Sha>> resolve(String text) {
        return CliHistory.resolve(worktree, text);
    }
}
