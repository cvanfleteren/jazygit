package net.vanfleteren.jazygit.kernel.state.launch;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.settings.Program;
import net.vanfleteren.jazygit.kernel.state.Terminal;

/**
 * Starts the programs the user set in the settings file, as an editor (ADR 110) or an agent (ADR 161) is: run without
 * a shell, in a directory, either handed the terminal until it exits, or started and left running (ADR 183). Starting
 * one is not git's business, so a feature asks this, not git.
 */
public interface ProgramLauncher {

    /**
     * Runs nothing, and says each program ran: for the tests.
     */
    ProgramLauncher NONE = new ProgramLauncher() {

        @Override
        public Either<ProgramFailure, ExecutedCommand> run(Path directory, List<String> commandLine) {
            return Either.right(new ExecutedCommand(commandLine));
        }

        @Override
        public Either<ProgramFailure, ExecutedCommand> start(Path directory, List<String> commandLine) {
            return Either.right(new ExecutedCommand(commandLine));
        }
    };

    /**
     * Starts programs as processes of this one, handing them {@code terminal} when they run on it.
     */
    static ProgramLauncher on(Terminal terminal) {
        return new ProcessLauncher(terminal);
    }

    /**
     * Runs {@code commandLine} in {@code directory} with the terminal handed over to it, and waits until it exits.
     */
    Either<ProgramFailure, ExecutedCommand> run(Path directory, List<String> commandLine);

    /**
     * Starts {@code commandLine} in {@code directory} and leaves it running, as a browser is (ADR 85): it gets no
     * input, and its output is thrown away. It fails only when it cannot be started, or exits with an error before it
     * is taken to have started (ADR 146).
     */
    Either<ProgramFailure, ExecutedCommand> start(Path directory, List<String> commandLine);

    /**
     * Runs {@code commandLine}, which {@code program} gave, in {@code directory} as the program says: {@link #run}
     * when it is handed the terminal, {@link #start} otherwise.
     */
    default Either<ProgramFailure, ExecutedCommand> launch(Program program, Path directory, List<String> commandLine) {
        return program.terminal() ? run(directory, commandLine) : start(directory, commandLine);
    }
}
