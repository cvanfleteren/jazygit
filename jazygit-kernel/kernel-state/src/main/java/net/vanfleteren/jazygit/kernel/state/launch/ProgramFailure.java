package net.vanfleteren.jazygit.kernel.state.launch;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Phrase;

/**
 * Why a program the user set did not run (ADR 183). Each carries the command line it was run as, so the command log
 * shows it as it shows a git command that failed (ADR 146).
 */
public sealed interface ProgramFailure extends Failure.Phrased {

    /**
     * The command line the program was run as, as the user wrote it with its placeholders filled in.
     */
    List<String> commandLine();

    /**
     * The program it runs, as the user wrote it.
     */
    default String program() {
        return commandLine().headOption().getOrElse("");
    }

    @Override
    default List<ExecutedCommand> commands() {
        return List.of(new ExecutedCommand(commandLine()));
    }

    /**
     * The program could not be started, as when it is not on the path.
     *
     * @param reason why, as the system said it
     */
    record NotStarted(List<String> commandLine, String reason) implements ProgramFailure {

        /**
         * Not started, for {@code cause}: what it says about why, or what kind it is when it says nothing.
         */
        static NotStarted of(List<String> commandLine, Throwable cause) {
            String message = cause.getMessage();
            return new NotStarted(
                    commandLine,
                    message == null || message.isBlank() ? cause.getClass().getSimpleName() : message);
        }

        @Override
        public Phrase phrase() {
            return Phrase.of("state.error.program.notStarted", program(), reason);
        }
    }

    /**
     * The program, handed the terminal, exited with a non-zero code.
     */
    record Failed(List<String> commandLine, int exitCode) implements ProgramFailure {

        @Override
        public Phrase phrase() {
            return Phrase.of("state.error.program.failed", program(), String.valueOf(exitCode));
        }
    }

    /**
     * The program, started and left running, exited with a non-zero code before it was taken to have started (ADR
     * 146). What it printed was thrown away; a program that needs a terminal, set without {@code terminal = true},
     * fails so.
     */
    record ExitedAtStart(List<String> commandLine, int exitCode) implements ProgramFailure {

        @Override
        public Phrase phrase() {
            return Phrase.of("state.error.program.exitedAtStart", program(), String.valueOf(exitCode));
        }
    }

    /**
     * Waiting for the program was interrupted.
     */
    record Interrupted(List<String> commandLine) implements ProgramFailure {

        @Override
        public Phrase phrase() {
            return Phrase.of("state.error.program.interrupted", program());
        }
    }
}
