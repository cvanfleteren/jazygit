package net.vanfleteren.jazygit.gh;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import java.nio.file.Path;
import java.util.function.Function;
import net.vanfleteren.jazygit.process.Exec;
import net.vanfleteren.jazygit.process.ExecFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Runs {@link GhCommand}s through the {@code gh} command line tool. The only place that starts gh.
 */
final class GhProcess {

    private final Path workTree;
    private final String executable;
    private final GhToken token;

    /**
     * Runs gh at {@code executable} in {@code workTree}, as the account of {@code token}.
     */
    GhProcess(Path workTree, String executable, GhToken token) {
        this.workTree = workTree;
        this.executable = executable;
        this.token = token;
    }

    /**
     * Whether gh failed as GitHub did not accept its credentials.
     */
    static boolean rejected(GhError error) {
        return error instanceof GhError.CommandFailed(var explanation, int exitCode)
                && explanation.exists(line -> line.contains("HTTP 401") || line.contains("Bad credentials"));
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
        return execute(command).flatMap(output -> Either.narrow(parse.apply(output.stdout())));
    }

    /**
     * Runs {@code command} as the configured account; once more with the token asked again, when GitHub did not
     * accept it, as it may have been replaced since.
     */
    private Either<GhError, Output> execute(GhCommand command) {
        return token.environment()
                .flatMap(variables -> execute(command, variables)
                        .fold(
                                // Without a configured account, gh uses its own, which asking again does not change.
                                error -> variables.isEmpty() || !rejected(error)
                                        ? Either.left(error)
                                        : executeAgain(command),
                                Either::right));
    }

    private Either<GhError, Output> execute(GhCommand command, Map<String, String> variables) {
        return Exec.run(
                        workTree,
                        command.commandLine(executable),
                        command.environment().merge(variables),
                        command.timeout())
                .<GhError>mapLeft(failure -> switch (failure) {
                    case ExecFailure.NotStarted(var cause) -> new GhError.GhUnavailable(cause);
                    case ExecFailure.TimedOut() -> new GhError.TimedOut(command.operation());
                    case ExecFailure.Interrupted() -> new GhError.Interrupted(command.operation());
                    case ExecFailure.NonZero(int exitCode, String stderr, String stdout) ->
                        new GhError.CommandFailed(Exec.explanation(stderr, stdout), exitCode);
                })
                .map(stdout -> new Output(stdout, command.executed()));
    }

    private Either<GhError, Output> executeAgain(GhCommand command) {
        token.forget();
        return token.environment().flatMap(variables -> execute(command, variables));
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
