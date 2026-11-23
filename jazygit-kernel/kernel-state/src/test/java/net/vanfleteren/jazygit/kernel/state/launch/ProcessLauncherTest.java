package net.vanfleteren.jazygit.kernel.state.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.settings.Program;
import net.vanfleteren.jazygit.kernel.state.Terminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Starting the programs the user set as processes (ADR 183).
 */
class ProcessLauncherTest {

    @TempDir
    Path directory;

    private final java.util.List<String> handedOver = new ArrayList<>();

    // A terminal that says when it was handed over.
    private final Terminal terminal = new Terminal() {
        @Override
        public <T> T handOver(Supplier<T> program) {
            handedOver.add("leave");
            T result = program.get();
            handedOver.add("come back");
            return result;
        }
    };

    private final ProgramLauncher launcher = ProgramLauncher.on(terminal);

    @Test
    void aProgramRunOnTheTerminalRunsInTheDirectoryWithTheTerminalHandedOver() throws Exception {
        List<String> commandLine = List.of("sh", "-c", "pwd > ran.txt");

        ExecutedCommand ran = launcher.run(directory, commandLine).get();

        assertEquals(
                directory.toRealPath().toString(),
                Files.readString(directory.resolve("ran.txt")).strip());
        assertEquals(new ExecutedCommand(commandLine), ran);
        assertEquals(java.util.List.of("leave", "come back"), handedOver);
    }

    @Test
    void aProgramRunOnTheTerminalThatFailsSaysItsProgramAndIsInTheLog() {
        List<String> commandLine = List.of("sh", "-c", "exit 3");

        ProgramFailure failure = launcher.run(directory, commandLine).getLeft();

        assertEquals(new ProgramFailure.Failed(commandLine, 3), failure);
        assertEquals("sh", failure.program());
        assertEquals(List.of(new ExecutedCommand(commandLine)), failure.commands());
    }

    @Test
    void aProgramThatCannotBeStartedOnTheTerminalIsInTheLog() {
        List<String> commandLine = List.of("jazygit-no-such-program", directory.toString());

        ProgramFailure failure = launcher.run(directory, commandLine).getLeft();

        assertInstanceOf(ProgramFailure.NotStarted.class, failure);
        assertEquals(List.of(new ExecutedCommand(commandLine)), failure.commands());
    }

    @Test
    void aProgramLeftRunningStartsInTheDirectoryWithoutTheTerminal() throws Exception {
        List<String> commandLine = List.of("sh", "-c", "pwd > started.txt");

        ExecutedCommand started = launcher.start(directory, commandLine).get();

        assertEquals(
                directory.toRealPath().toString(),
                Files.readString(directory.resolve("started.txt")).strip());
        assertEquals(new ExecutedCommand(commandLine), started);
        assertEquals(java.util.List.of(), handedOver);
    }

    @Test
    void aProgramLeftRunningThatCannotBeStartedSaysItsProgram() {
        ProgramFailure failure = launcher.start(directory, List.of("jazygit-no-such-program", directory.toString()))
                .getLeft();

        ProgramFailure.NotStarted notStarted = assertInstanceOf(ProgramFailure.NotStarted.class, failure);
        assertEquals("jazygit-no-such-program", notStarted.program());
    }

    @Test
    void aProgramLeftRunningThatExitsWithAnErrorAtOnceFailsAndIsInTheLog() {
        List<String> commandLine = List.of("sh", "-c", "exit 2", directory.toString());

        ProgramFailure failure = launcher.start(directory, commandLine).getLeft();

        assertEquals(new ProgramFailure.ExitedAtStart(commandLine, 2), failure);
        assertEquals(List.of(new ExecutedCommand(commandLine)), failure.commands());
    }

    @Test
    void aProgramIsRunAsItSays() throws Exception {
        launcher.launch(program(true), directory, List.of("sh", "-c", "true")).get();
        assertEquals(java.util.List.of("leave", "come back"), handedOver);

        handedOver.clear();
        launcher.launch(program(false), directory, List.of("sh", "-c", "true")).get();
        assertEquals(java.util.List.of(), handedOver);
    }

    private static Program program(boolean onTheTerminal) {
        return new Program() {
            @Override
            public String name() {
                return "program";
            }

            @Override
            public boolean terminal() {
                return onTheTerminal;
            }

            @Override
            public Option<Character> shortcut() {
                return Option.none();
            }
        };
    }
}
