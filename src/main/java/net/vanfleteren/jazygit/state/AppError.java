package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;

/**
 * What the user is told went wrong, as data; the view turns it into text.
 */
public sealed interface AppError {

    /**
     * An operation on the repository failed.
     *
     * @param subject what it was done to, e.g. the branch that was pushed, if the operation names one
     */
    record OperationFailed(Operation operation, Option<String> subject, Failure failure) implements AppError {
    }

    /**
     * A commit was asked for, but nothing has changed.
     */
    record NothingToCommit() implements AppError {
    }

    /**
     * Amending was asked for, but nothing has changed to amend the last commit with.
     */
    record NothingToAmend() implements AppError {
    }

    /**
     * A stash was asked for, but nothing has changed.
     */
    record NothingToStash() implements AppError {
    }

    /**
     * The upstream entered for a push is not {@code <remote> <branch>}.
     */
    record InvalidUpstream(String text) implements AppError {
    }
}
