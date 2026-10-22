package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.function.Function;
import net.vanfleteren.jazygit.git.GitError.*;
import net.vanfleteren.jazygit.process.Exec;
import net.vanfleteren.jazygit.process.ExecFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Runs {@link GitCommand}s through the {@code git} command line tool, with {@link Exec}. The only place that
 * starts git.
 */
final class GitProcess {

    private GitProcess() {}

    /**
     * Runs {@code command} in {@code worktree}. A non-zero exit code is a {@link CommandFailed}.
     */
    static Either<GitFailure<ProcessError>, Output> run(Path worktree, GitCommand command) {
        return execute(worktree, command).mapLeft(error -> new GitFailure<>(error, List.of(command.executed())));
    }

    /**
     * Runs {@code command} for its effect, and returns the command log.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> log(Path worktree, GitCommand command) {
        return run(worktree, command).map(Output::log);
    }

    /**
     * Runs {@code command} to read something, which {@code parse} makes of its output. Reads are not logged.
     */
    static <T> Either<ProcessError, T> read(
            Path worktree, GitCommand command, Function<String, Either<UnexpectedOutput, T>> parse) {
        return execute(worktree, command).flatMap(output -> Either.narrow(parse.apply(output.stdout())));
    }

    /**
     * Runs {@code command}, a question git answers "no" to by exiting with 1 without saying anything, like
     * {@code git diff --quiet} or {@code git config --get} for a key that is not set. That answer is none.
     */
    static Either<GitFailure<ProcessError>, Option<Output>> ask(Path worktree, GitCommand command) {
        return run(worktree, command)
                .fold(
                        failure -> failure.error() instanceof CommandFailed failed && failed.silent()
                                ? Either.right(Option.none())
                                : Either.left(failure),
                        output -> Either.right(Option.of(output)));
    }

    private static Either<ProcessError, Output> execute(Path worktree, GitCommand command) {
        return Exec.run(worktree, command.commandLine(), command.environment(), command.timeout())
                .<ProcessError>mapLeft(failure -> switch (failure) {
                    case ExecFailure.NotStarted(var cause) -> new GitUnavailable(cause);
                    case ExecFailure.TimedOut() -> new TimedOut(command.operation(), worktree);
                    case ExecFailure.Interrupted() -> new Interrupted(command.operation());
                    case ExecFailure.NonZero(int exitCode, String stderr, String stdout) ->
                        new CommandFailed(Exec.explanation(stderr, stdout), exitCode, stderr);
                })
                .map(stdout -> new Output(stdout, command.executed()));
    }

    /**
     * What a command that succeeded printed on its standard output, and the line it was run as.
     */
    record Output(String stdout, ExecutedCommand command) {

        /**
         * The command log of this one command.
         */
        List<ExecutedCommand> log() {
            return List.of(command);
        }
    }
}
