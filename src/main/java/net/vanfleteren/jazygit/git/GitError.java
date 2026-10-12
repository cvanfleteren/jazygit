package net.vanfleteren.jazygit.git;

import java.nio.file.Path;

/**
 * Why a git operation failed, as data. Turning it into text is up to the caller.
 *
 * <p>The marker interfaces name the failures one operation can have, so its signature tells them
 * all. They nest: every {@link ProcessError} is a {@link CheckoutError}, as a checkout runs a git
 * command, and every {@link CheckoutError} is a {@link StashCheckoutError}, as that runs a checkout.
 */
public sealed interface GitError {

    /**
     * The failures of checking out a branch.
     */
    sealed interface CheckoutError extends StashCheckoutError {
    }

    /**
     * The failures of checking out a branch while taking the uncommitted changes along.
     */
    sealed interface StashCheckoutError extends GitError {
    }

    /**
     * The failures of rewording a commit before the last one.
     */
    sealed interface RewordError extends GitError {
    }

    /**
     * The failures of throwing changes away.
     */
    sealed interface DiscardError extends GitError {
    }

    /**
     * The failures of reading the diff of changed files.
     */
    sealed interface DiffError extends GitError {
    }

    /**
     * The failures of reading the log of a branch.
     */
    sealed interface LogError extends GitError {
    }

    /**
     * The failures of opening a repository.
     */
    sealed interface OpenError extends GitError {
    }

    /**
     * A failure that running any git command can have.
     */
    sealed interface ProcessError extends CheckoutError, RewordError, DiscardError, DiffError, LogError, OpenError {
    }

    /**
     * The {@code git} executable could not be started.
     */
    record GitUnavailable() implements ProcessError {
    }

    /**
     * @param operation the git subcommand, e.g. {@code checkout}
     */
    record TimedOut(String operation, Path workTree) implements ProcessError {
    }

    /**
     * @param operation the git subcommand, e.g. {@code checkout}
     */
    record Interrupted(String operation) implements ProcessError {
    }

    /**
     * Git ran, but refused.
     *
     * @param explanation the first line git printed about why, preferring its error output; empty if it
     *                    printed nothing
     */
    record CommandFailed(String explanation, int exitCode) implements ProcessError {
    }

    /**
     * No git repository contains the directory.
     */
    record NotARepository(Path directory) implements OpenError {
    }

    /**
     * The repository is bare: there are no files to show.
     *
     * @param directory the directory the repository was looked for from
     */
    record NoWorkTree(Path directory) implements OpenError {
    }

    /**
     * There is no local branch by that name.
     */
    record UnknownBranch(String branch) implements LogError {
    }

    /**
     * Git refused a checkout because uncommitted changes would be overwritten. Stashing them first lets
     * it go through.
     *
     * @param explanation git's explanation
     */
    record LocalChanges(String explanation) implements CheckoutError {
    }

    /**
     * The branch was checked out, but the stashed changes could not be applied on it, so they stay in
     * the stash.
     */
    record StashNotRestored(ProcessError cause) implements StashCheckoutError {
    }

    /**
     * A temporary file git needed could not be written.
     */
    record TempFileFailed(String reason) implements RewordError, DiffError {
    }

    /**
     * An untracked path that was not deleted because it is not inside the working tree.
     */
    record RefusedPath(String path) implements DiscardError {
    }

    /**
     * An untracked path that could not be deleted.
     */
    record DeleteFailed(String path, String reason) implements DiscardError {
    }
}
