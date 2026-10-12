package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;

import java.util.function.Function;
import java.util.function.Supplier;

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
     * This failure, with the {@code later} commands that were run to clean up after it.
     */
    public GitFailure<E> followedBy(List<String> later) {
        return new GitFailure<>(error, commands.appendAll(later));
    }

    /**
     * The same commands, failing for the reason {@code reason} makes of this error.
     */
    public <F extends GitError> GitFailure<F> map(Function<? super E, ? extends F> reason) {
        return new GitFailure<>(reason.apply(error), commands);
    }

    /**
     * The failure seen as one of a wider kind of error.
     */
    public static <E extends GitError> GitFailure<E> widen(GitFailure<? extends E> failure) {
        return new GitFailure<>(failure.error(), failure.commands());
    }

    /**
     * The outcome with its failure seen as one of a wider kind of error.
     */
    public static <E extends GitError, T> Either<GitFailure<E>, T> widen(
            Either<? extends GitFailure<? extends E>, ? extends T> outcome) {
        return outcome.fold(failure -> Either.left(widen(failure)), Either::right);
    }

    /**
     * Runs the steps in order and returns all command lines they executed. The first step that fails
     * stops the sequence, and its failure also carries the commands of the steps before it.
     */
    @SafeVarargs
    public static <E extends GitError> Either<GitFailure<E>, List<String>> sequence(
            Supplier<Either<GitFailure<E>, List<String>>>... steps) {
        return sequence(List.of(steps));
    }

    /**
     * Runs the steps in order and returns all command lines they executed. The first step that fails
     * stops the sequence, and its failure also carries the commands of the steps before it.
     */
    public static <E extends GitError> Either<GitFailure<E>, List<String>> sequence(
            List<Supplier<Either<GitFailure<E>, List<String>>>> steps) {
        return steps.foldLeft(Either.right(List.empty()), (done, step) -> done.flatMap(earlier ->
                step.get().map(earlier::appendAll).mapLeft(failure -> failure.after(earlier))));
    }

    /**
     * Bridges to callers that still expect an exception.
     */
    GitCommandException toException() {
        return error instanceof GitError.LocalChanges(String explanation)
                ? new LocalChangesException(explanation, commands)
                : new GitCommandException(GitErrorText.of(error), commands);
    }
}
