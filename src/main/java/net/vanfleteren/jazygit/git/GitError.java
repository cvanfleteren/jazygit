package net.vanfleteren.jazygit.git;

import java.nio.file.Path;

/**
 * Why a git operation failed, as data. Turning it into text is up to the caller.
 */
public sealed interface GitError {

    /**
     * A failure that running any git command can have.
     */
    sealed interface ProcessError extends GitError {
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
}
