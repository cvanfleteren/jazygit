package net.vanfleteren.jazygit.kernel.state.launch;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.nio.file.Path;
import java.time.Duration;
import net.vanfleteren.jazygit.core.process.Exec;
import net.vanfleteren.jazygit.core.process.ExecFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.state.Terminal;

/**
 * Starts programs as processes of this one, handing them {@code terminal} when they run on it (ADR 183).
 */
record ProcessLauncher(Terminal terminal) implements ProgramLauncher {

    // How long a program that is left running is waited for: one still running by then is taken to have started, as
    // a browser is (ADR 85).
    static final Duration SETTLE = Duration.ofSeconds(2);

    @Override
    public Either<ProgramFailure, ExecutedCommand> run(Path directory, List<String> commandLine) {
        return terminal.handOver(() -> Exec.interactive(directory, commandLine))
                .mapLeft(failure -> failed(commandLine, failure));
    }

    @Override
    public Either<ProgramFailure, ExecutedCommand> start(Path directory, List<String> commandLine) {
        return Exec.launch(directory, commandLine, SETTLE)
                .mapLeft(failure -> failure instanceof ExecFailure.NonZero(int exitCode, var stderr, var stdout)
                        ? new ProgramFailure.ExitedAtStart(commandLine, exitCode)
                        : failed(commandLine, failure));
    }

    private static ProgramFailure failed(List<String> commandLine, ExecFailure failure) {
        return switch (failure) {
            case ExecFailure.NotStarted(var cause) -> ProgramFailure.NotStarted.of(commandLine, cause);
            case ExecFailure.NonZero(int exitCode, var stderr, var stdout) ->
                new ProgramFailure.Failed(commandLine, exitCode);
            case ExecFailure.Interrupted() -> new ProgramFailure.Interrupted(commandLine);
            // A program gets as long as the user takes.
            case ExecFailure.TimedOut() -> new ProgramFailure.Interrupted(commandLine);
            // Its output goes to the terminal, or is thrown away, not read by jazygit.
            case ExecFailure.TooMuchOutput() -> new ProgramFailure.Interrupted(commandLine);
        };
    }
}
