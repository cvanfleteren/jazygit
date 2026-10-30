package net.vanfleteren.jazygit.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecTest {

    @TempDir
    Path dir;

    @Test
    void aProgramThatExitsWithZeroReturnsWhatItPrinted() {
        assertEquals(Either.right("hi\n"), sh("echo hi", HashMap.empty()));
    }

    @Test
    void aProgramThatFailsKeepsItsExitCodeAndBothOutputsApart() {
        assertEquals(
                Either.left(new ExecFailure.NonZero(3, "err\n", "out\n")),
                sh("echo out; echo err >&2; exit 3", HashMap.empty()));
    }

    @Test
    void theEnvironmentIsSetForTheProgram() {
        assertEquals(Either.right("y"), sh("printf %s \"$X\"", HashMap.of("X", "y")));
    }

    @Test
    void aProgramThatDoesNotExistIsNotStarted() {
        Either<ExecFailure, String> result =
                Exec.run(dir, List.of("jazygit-no-such-program"), HashMap.empty(), Duration.ofSeconds(10));

        assertInstanceOf(ExecFailure.NotStarted.class, result.getLeft());
    }

    @Test
    void aProgramThatTakesTooLongTimesOut() {
        assertEquals(
                Either.left(new ExecFailure.TimedOut()),
                Exec.run(dir, List.of("sleep", "5"), HashMap.empty(), Duration.ofMillis(50)));
    }

    @Test
    void anInteractiveProgramThatExitsWithZeroReturnsTheCommand() {
        assertEquals(
                Either.right(ExecutedCommand.of("sh", "-c", "exit 0")),
                Exec.interactive(dir, List.of("sh", "-c", "exit 0")));
    }

    @Test
    void anInteractiveProgramThatFailsKeepsItsExitCode() {
        assertEquals(
                Either.left(new ExecFailure.NonZero(2, "", "")), Exec.interactive(dir, List.of("sh", "-c", "exit 2")));
    }

    @Test
    void anInteractiveProgramThatDoesNotExistIsNotStarted() {
        assertInstanceOf(
                ExecFailure.NotStarted.class,
                Exec.interactive(dir, List.of("jazygit-no-such-program")).getLeft());
    }

    @Test
    void theExplanationPrefersTheErrorOutput() {
        assertEquals(Option.of("error: boom"), Exec.explanation("\n error: boom\nmore", "On branch main"));
        assertEquals(Option.of("On branch main"), Exec.explanation("", "\nOn branch main\n"));
        assertEquals(Option.none(), Exec.explanation("", ""));
    }

    @Test
    void theExplanationIsTheLineThatSaysWhatWentWrong() {
        String rejected = """
                To github.com:o/r.git
                 ! [rejected]        main -> main (fetch first)
                error: failed to push some refs to 'github.com:o/r.git'
                hint: Updates were rejected because the remote contains work that you do not have
                """;

        assertEquals(
                Option.of("error: failed to push some refs to 'github.com:o/r.git'"), Exec.explanation(rejected, ""));
    }

    private Either<ExecFailure, String> sh(String script, Map<String, String> environment) {
        return Exec.run(dir, List.of("sh", "-c", script), environment, Duration.ofSeconds(10));
    }
}
