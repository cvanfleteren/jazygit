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

    private final Path workTree;
    private final String executable;

    /**
     * Runs gh at {@code executable} in {@code workTree}.
     */
    GhProcess(Path workTree, String executable) {
        this.workTree = workTree;
        this.executable = executable;
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
    Either<GhFailure, Output> run(GhCommand command) {
        return execute(command).mapLeft(error -> new GhFailure(error, List.of(command.executed())));
    }

    /**
     * Runs {@code command} for its effect, and returns the command log.
     */
    Either<GhFailure, List<ExecutedCommand>> log(GhCommand command) {
        return run(command).map(Output::log);
    }

    /**
     * Runs {@code command} to read something, which {@code parse} makes of its output. Reads are not logged.
     */
    <T> Either<GhError, T> read(GhCommand command, Function<String, Either<GhError.UnexpectedOutput, T>> parse) {
        return execute(command).flatMap(output -> Either.<GhError, T>narrow(parse.apply(output.stdout())));
    }

    private Either<GhError, Output> execute(GhCommand command) {
        return Exec.run(workTree, command.commandLine(executable), command.environment(), command.timeout())
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
