package net.vanfleteren.jazygit.git.process;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.util.function.Function;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * A git operation that failed: why, and the command lines that were executed up to and including the
 * failing one, so they can still be logged.
 *
 * @param cleanupFailed why cleaning up after the failure, e.g. aborting a rebase, failed too, if it did
 */
public record GitFailure(GitError error, List<ExecutedCommand> commands, Option<GitError> cleanupFailed) {

    public GitFailure(GitError error, List<ExecutedCommand> commands) {
        this(error, commands, Option.none());
    }

    /**
     * Nothing to do: no command ran, and nothing failed.
     */
    public static Either<GitFailure, List<ExecutedCommand>> nothing() {
        return Either.right(List.empty());
    }

    /**
     * Runs the steps in order and returns all command lines they executed. The first step that fails
     * stops the sequence, and its failure also carries the commands of the steps before it.
     */
    @SafeVarargs
    public static Either<GitFailure, List<ExecutedCommand>> sequence(
            Supplier<Either<GitFailure, List<ExecutedCommand>>>... steps) {
        return sequence(List.of(steps));
    }

    /**
     * Runs the steps in order and returns all command lines they executed. The first step that fails
     * stops the sequence, and its failure also carries the commands of the steps before it.
     */
    public static Either<GitFailure, List<ExecutedCommand>> sequence(
            List<Supplier<Either<GitFailure, List<ExecutedCommand>>>> steps) {
        return steps.foldLeft(
                nothing(),
                (done, step) -> done.flatMap(
                        earlier -> step.get().map(earlier::appendAll).mapLeft(failure -> failure.after(earlier))));
    }

    /**
     * This failure, as part of a sequence in which the {@code earlier} commands already succeeded.
     */
    public GitFailure after(List<ExecutedCommand> earlier) {
        return new GitFailure(error, earlier.appendAll(commands), cleanupFailed);
    }

    /**
     * This failure, followed by the {@code cleanup} that was run after it: its commands are added, and when
     * it failed too, so is why.
     */
    public GitFailure followedBy(Either<GitFailure, List<ExecutedCommand>> cleanup) {
        return cleanup.fold(
                failed -> new GitFailure(error, commands.appendAll(failed.commands()), Option.of(failed.error())),
                done -> new GitFailure(error, commands.appendAll(done), cleanupFailed));
    }

    /**
     * The same commands, failing for the reason {@code reason} makes of this error.
     */
    public GitFailure map(Function<GitError, GitError> reason) {
        return new GitFailure(reason.apply(error), commands, cleanupFailed);
    }
}
