package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;

/**
 * A git operation that failed: why, and the command lines that were executed up to and including the
 * failing one, so they can still be logged.
 */
public record GitFailure<E extends GitError>(E error, List<String> commands) {

    /**
     * This failure, as part of a sequence in which the {@code earlier} commands already succeeded.
     */
    public GitFailure<E> after(List<String> earlier) {
        return new GitFailure<>(error, earlier.appendAll(commands));
    }

    /**
     * The failure seen as one of a wider kind of error.
     */
    public static <E extends GitError> GitFailure<E> widen(GitFailure<? extends E> failure) {
        return new GitFailure<>(failure.error(), failure.commands());
    }

    /**
     * Bridges to callers that still expect an exception.
     */
    GitCommandException toException() {
        return new GitCommandException(GitErrorText.of(error), commands);
    }
}
