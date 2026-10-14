package net.vanfleteren.jazygit.git;

import io.vavr.control.Option;

import java.io.IOException;
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
     * The failures of applying or deleting a stash.
     */
    sealed interface StashError extends GitError {
    }

    /**
     * The failures of deleting untracked files.
     */
    sealed interface DeleteUntrackedError extends DiscardError {
    }

    /**
     * A failure that running any git command can have.
     */
    sealed interface ProcessError extends CheckoutError, RewordError, DiscardError, DiffError, LogError, OpenError,
            StashError {
    }

    /**
     * The {@code git} executable could not be started.
     *
     * @param cause why the process could not be started
     */
    record GitUnavailable(IOException cause) implements ProcessError {
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
     * @param explanation the line git printed about why, preferring its error output; none if it printed
     *                    nothing
     * @param output      everything git printed on its error output
     */
    record CommandFailed(Option<String> explanation, int exitCode, String output) implements ProcessError {

        public CommandFailed(String explanation, int exitCode) {
            this(Option.of(explanation).filter(text -> !text.isEmpty()), exitCode, explanation);
        }

        /**
         * Whether git refused without saying anything, with exit code 1. That is how commands that answer
         * a question say "no", e.g. {@code git diff --quiet} when there are differences.
         */
        public boolean silent() {
            return exitCode == 1 && explanation.isEmpty();
        }

        /**
         * Whether git printed {@code text}, in its explanation or elsewhere on its error output.
         */
        public boolean says(String text) {
            return explanation.exists(line -> line.contains(text)) || output.contains(text);
        }
    }

    /**
     * Git printed something that could not be made sense of.
     *
     * @param output the part of the output that could not be parsed
     */
    record UnexpectedOutput(String output) implements ProcessError {
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
     * The stash is not there anymore: it was popped or dropped since it was listed.
     *
     * @param message what it was stashed as
     */
    record StashGone(String message) implements StashError {
    }

    /**
     * A temporary file git needed could not be written.
     */
    record TempFileFailed(Throwable cause) implements RewordError, DiffError {
    }

    /**
     * An untracked path that was not deleted because it is not inside the working tree.
     */
    record RefusedPath(String path) implements DeleteUntrackedError {
    }

    /**
     * An untracked path that could not be deleted.
     */
    record DeleteFailed(String path, Throwable cause) implements DeleteUntrackedError {
    }
}
