package net.vanfleteren.jazygit.gh;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Function;
import net.vanfleteren.jazygit.json.Decoder;
import net.vanfleteren.jazygit.process.Exec;
import net.vanfleteren.jazygit.process.ExecFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.process.Limit;
import net.vanfleteren.jazygit.process.Limited;

/**
 * Runs {@link GhCommand}s through the {@code gh} command line tool. The only place that starts gh.
 */
final class GhProcess {

    // The version of gh that brought --slurp, which reads all pages as one JSON array (ADR 42).
    static final String SLURP_SINCE = "2.48";

    private final Path worktree;
    private final String executable;
    private final GhToken token;

    /**
     * Runs gh at {@code executable} in {@code worktree}, as the account of {@code token}.
     */
    GhProcess(Path worktree, String executable, GhToken token) {
        this.worktree = worktree;
        this.executable = executable;
        this.token = token;
    }

    /**
     * Runs the same gh, as the same account, in {@code directory}, taken relative to the working tree of this one.
     */
    GhProcess in(Path directory) {
        return new GhProcess(worktree.resolve(directory).normalize(), executable, token);
    }

    /**
     * Whether gh failed as GitHub did not accept its credentials.
     */
    static boolean rejected(GhError error) {
        return error instanceof GhError.CommandFailed(var explanation, int exitCode)
                && explanation.exists(line -> line.contains("HTTP 401") || line.contains("Bad credentials"));
    }

    /**
     * Whether gh failed as GitHub has no node with the id it was given, as when it was deleted already: GraphQL then
     * answers {@code NOT_FOUND}, which gh prints as this.
     */
    static boolean gone(GhError error) {
        return error instanceof GhError.CommandFailed(var explanation, int exitCode)
                && explanation.exists(line -> line.contains("Could not resolve to a node with the global id"));
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
     * Runs {@code command} to read what it prints, only up to {@code limit}: once it printed more, it is stopped and
     * what it printed is cut off (ADR 79). Reads are not logged.
     */
    Either<GhError, Limited> read(GhCommand command, Limit limit) {
        return execute(
                command,
                (directory, line, environment, timeout) -> Exec.read(directory, line, environment, timeout, limit));
    }

    /**
     * Runs {@code command} to read the JSON it prints, which {@code decoder} decodes (ADR 42). JSON that cannot be
     * parsed or decoded is unexpected output, which says where and why.
     */
    <T> Either<GhError, T> readJson(GhCommand command, Decoder<T> decoder) {
        return read(command, stdout -> decoded(stdout, decoder));
    }

    /**
     * What {@code decoder} makes of the JSON {@code stdout}, or the unexpected output that says where and why it
     * could not.
     */
    static <T> Either<GhError.UnexpectedOutput, T> decoded(String stdout, Decoder<T> decoder) {
        return decoder.parse(stdout).mapLeft(error -> new GhError.UnexpectedOutput(error.text()));
    }

    private Either<GhError, Output> execute(GhCommand command) {
        return execute(command, Exec::run).map(stdout -> new Output(stdout, command.executed()));
    }

    /**
     * Runs {@code command} with {@code execution} as the configured account; once more with the token asked again,
     * when GitHub did not accept it, as it may have been replaced since.
     */
    private <T> Either<GhError, T> execute(GhCommand command, Execution<T> execution) {
        return token.environment()
                .flatMap(variables -> execute(command, variables, execution)
                        .fold(
                                // Without a configured account, gh uses its own, which asking again does not change.
                                error -> variables.isEmpty() || !rejected(error)
                                        ? Either.left(error)
                                        : executeAgain(command, execution),
                                Either::right));
    }

    private <T> Either<GhError, T> execute(GhCommand command, Map<String, String> variables, Execution<T> execution) {
        return execution
                .run(
                        worktree,
                        command.commandLine(executable),
                        command.environment().merge(variables),
                        command.timeout())
                .<GhError>mapLeft(failure -> switch (failure) {
                    case ExecFailure.NotStarted(var cause) -> new GhError.GhUnavailable(cause);
                    case ExecFailure.TimedOut() -> new GhError.TimedOut(command.operation());
                    case ExecFailure.Interrupted() -> new GhError.Interrupted(command.operation());
                    case ExecFailure.TooMuchOutput() -> new GhError.TooMuchOutput(command.operation());
                    case ExecFailure.NonZero(int exitCode, String stderr, String stdout) ->
                        tooOld(stderr)
                                ? new GhError.TooOld(SLURP_SINCE)
                                : new GhError.CommandFailed(Exec.explanation(stderr, stdout), exitCode);
                });
    }

    /**
     * Whether gh failed as it does not know {@code --slurp} yet, which it says before anything else.
     */
    static boolean tooOld(String stderr) {
        return stderr.contains("unknown flag: --slurp");
    }

    private <T> Either<GhError, T> executeAgain(GhCommand command, Execution<T> execution) {
        token.forget();
        return token.environment().flatMap(variables -> execute(command, variables, execution));
    }

    /**
     * How gh is run: as {@link Exec#run}, or as {@link Exec#read} with a limit.
     */
    @FunctionalInterface
    private interface Execution<T> {
        Either<ExecFailure, T> run(
                Path directory, List<String> commandLine, Map<String, String> environment, Duration timeout);
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
