package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A git operation that failed: why, and the command lines that were executed up to and including the
 * failing one, so they can still be logged.
 *
 * @param cleanupFailed why cleaning up after the failure, e.g. aborting a rebase, failed too, if it did
 */
public record GitFailure<E extends GitError>(E error, List<ExecutedCommand> commands, Option<ProcessError> cleanupFailed) {

    public GitFailure(E error, List<ExecutedCommand> commands) {
        this(error, commands, Option.none());
    }

    /**
     * This failure, as part of a sequence in which the {@code earlier} commands already succeeded.
     */
    public GitFailure<E> after(List<ExecutedCommand> earlier) {
        return new GitFailure<>(error, earlier.appendAll(commands), cleanupFailed);
    }

    /**
     * This failure, followed by the {@code cleanup} that was run after it: its commands are added, and when
     * it failed too, so is why.
     */
    public GitFailure<E> followedBy(Either<GitFailure<ProcessError>, List<ExecutedCommand>> cleanup) {
        return cleanup.fold(
                failed -> new GitFailure<>(error, commands.appendAll(failed.commands()), Option.of(failed.error())),
                done -> new GitFailure<>(error, commands.appendAll(done), cleanupFailed));
    }

    /**
     * The same commands, failing for the reason {@code reason} makes of this error.
     */
    public <F extends GitError> GitFailure<F> map(Function<? super E, ? extends F> reason) {
        return new GitFailure<>(reason.apply(error), commands, cleanupFailed);
    }

    /**
     * The failure seen as one of a wider kind of error.
     */
    public static <E extends GitError> GitFailure<E> widen(GitFailure<? extends E> failure) {
        return new GitFailure<>(failure.error(), failure.commands(), failure.cleanupFailed());
    }

    /**
     * The outcome with its failure seen as one of a wider kind of error.
     */
    public static <E extends GitError, T> Either<GitFailure<E>, T> widen(
            Either<? extends GitFailure<? extends E>, ? extends T> outcome) {
        return outcome.<GitFailure<E>>mapLeft(GitFailure::widen).map(value -> value);
    }

    /**
     * Nothing to do: no command ran, and nothing failed.
     */
    public static <E extends GitError> Either<GitFailure<E>, List<ExecutedCommand>> nothing() {
        return Either.right(List.empty());
    }

    /**
     * Runs the steps in order and returns all command lines they executed. The first step that fails
     * stops the sequence, and its failure also carries the commands of the steps before it.
     */
    @SafeVarargs
    public static <E extends GitError> Either<GitFailure<E>, List<ExecutedCommand>> sequence(
            Supplier<Either<GitFailure<E>, List<ExecutedCommand>>>... steps) {
        return sequence(List.of(steps));
    }

    /**
     * Runs the steps in order and returns all command lines they executed. The first step that fails
     * stops the sequence, and its failure also carries the commands of the steps before it.
     */
    public static <E extends GitError> Either<GitFailure<E>, List<ExecutedCommand>> sequence(
            List<Supplier<Either<GitFailure<E>, List<ExecutedCommand>>>> steps) {
        return steps.foldLeft(nothing(), (done, step) -> done.flatMap(earlier ->
                step.get().map(earlier::appendAll).mapLeft(failure -> failure.after(earlier))));
    }
}
