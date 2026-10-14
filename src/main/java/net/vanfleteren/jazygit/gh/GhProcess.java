package net.vanfleteren.jazygit.gh;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.process.Exec;
import net.vanfleteren.jazygit.process.ExecFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;

import java.nio.file.Path;
import java.util.function.Function;

/**
 * Runs {@link GhCommand}s through the {@code gh} command line tool. The only place that starts gh.
 */
final class GhProcess {

    private GhProcess() {
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

    /**
     * Runs {@code command} for its effect, and returns what it printed with the line it was run as.
     */
    static Either<GhFailure, Output> run(Path workTree, GhCommand command) {
        return execute(workTree, command).mapLeft(error -> new GhFailure(error, List.of(command.executed())));
    }

    /**
     * Runs {@code command} for its effect, and returns the command log.
     */
    static Either<GhFailure, List<ExecutedCommand>> log(Path workTree, GhCommand command) {
        return run(workTree, command).map(Output::log);
    }

    /**
     * Runs {@code command} to read something, which {@code parse} makes of its output. Reads are not logged.
     */
    static <T> Either<GhError, T> read(Path workTree, GhCommand command,
                                       Function<String, Either<GhError.UnexpectedOutput, T>> parse) {
        return execute(workTree, command).flatMap(output -> Either.<GhError, T>narrow(parse.apply(output.stdout())));
    }

    private static Either<GhError, Output> execute(Path workTree, GhCommand command) {
        return Exec.run(workTree, command.commandLine(), command.environment(), command.timeout())
                .<GhError>mapLeft(failure -> switch (failure) {
                    case ExecFailure.NotStarted(var cause) -> new GhError.GhUnavailable(cause);
                    case ExecFailure.TimedOut() -> new GhError.TimedOut(command.operation());
                    case ExecFailure.Interrupted() -> new GhError.Interrupted(command.operation());
                    case ExecFailure.NonZero(int exitCode, String stderr, String stdout) ->
                            new GhError.CommandFailed(Exec.explanation(stderr, stdout), exitCode);
                })
                .map(stdout -> new Output(stdout, command.executed()));
    }
}
