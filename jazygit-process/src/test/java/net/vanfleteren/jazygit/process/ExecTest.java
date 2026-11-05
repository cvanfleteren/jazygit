package net.vanfleteren.jazygit.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // Long enough for a program that ends at once to have ended.
    private static final Duration SETTLE = Duration.ofSeconds(5);

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
    void onlyTheStartOfALongErrorOutputIsKept() {
        ExecFailure.NonZero failed =
                (ExecFailure.NonZero) sh("head -c 1000000 /dev/zero | tr '\\0' x >&2; exit 1", HashMap.empty())
                        .getLeft();

        assertEquals("x".repeat(Exec.MAX_STDERR), failed.stderr());
    }

    @Test
    void aProgramThatPrintsWithoutEndIsStoppedAtTheLimit() {
        long started = System.nanoTime();

        Either<ExecFailure, Limited> read =
                Exec.read(dir, List.of("yes"), HashMap.empty(), Duration.ofSeconds(30), new Limit(1000, 1 << 20));

        assertEquals(Either.right(new Limited("y\n".repeat(1000), true)), read);
        assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(10)) < 0);
    }

    @Test
    void aProgramThatPrintsOneEndlessLineIsStoppedAtTheLimit() {
        Either<ExecFailure, Limited> read = Exec.read(
                dir,
                List.of("sh", "-c", "tr '\\0' x < /dev/zero"),
                HashMap.empty(),
                Duration.ofSeconds(30),
                new Limit(1000, 1 << 20));

        assertEquals(Either.right(new Limited("", true)), read);
    }

    @Test
    void whatAProgramStartedIsStoppedWithIt() {
        // The shell waits for yes, which holds the output open as long as it runs.
        Either<ExecFailure, Limited> read = Exec.read(
                dir, List.of("sh", "-c", "yes; true"), HashMap.empty(), Duration.ofSeconds(30), new Limit(10, 1000));

        assertEquals(Either.right(new Limited("y\n".repeat(10), true)), read);
    }

    @Test
    void aProgramWithinTheLimitIsReadWholeAndMayFail() {
        assertEquals(
                Either.right(new Limited("a\nb\n", false)),
                Exec.read(
                        dir, List.of("printf", "a\\nb\\n"), HashMap.empty(), Duration.ofSeconds(10), new Limit(2, 4)));
        assertEquals(
                Either.left(new ExecFailure.NonZero(1, "", "a\n")),
                Exec.read(
                        dir,
                        List.of("sh", "-c", "echo a; exit 1"),
                        HashMap.empty(),
                        Duration.ofSeconds(10),
                        new Limit(2, 4)));
    }

    @Test
    void aReadThatTakesTooLongKeepsTheWholeLinesPrintedUntilThen() {
        assertEquals(
                Either.right(new Limited("a\nb\n", true)),
                Exec.read(
                        dir,
                        List.of("sh", "-c", "printf 'a\\nb\\nhalf'; exec sleep 5"),
                        HashMap.empty(),
                        Duration.ofMillis(500),
                        new Limit(10, 100)));
    }

    @Test
    void aReadThatTakesTooLongWithoutAWholeLineTimesOut() {
        assertEquals(
                Either.left(new ExecFailure.TimedOut()),
                Exec.read(
                        dir,
                        List.of("sh", "-c", "printf half; exec sleep 5"),
                        HashMap.empty(),
                        Duration.ofMillis(500),
                        new Limit(10, 100)));
    }

    @Test
    void aRunThatTakesTooLongTimesOutWhateverItPrinted() {
        assertEquals(
                Either.left(new ExecFailure.TimedOut()),
                Exec.run(
                        dir,
                        List.of("sh", "-c", "printf 'a\\n'; exec sleep 5"),
                        HashMap.empty(),
                        Duration.ofMillis(500)));
    }

    @Test
    void aProgramThatPrintsMoreThanIsEverReadFails() {
        assertEquals(Either.left(new ExecFailure.TooMuchOutput()), sh("yes", HashMap.empty()));
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
    void aLaunchedProgramIsLeftRunningOnceItSettled() {
        long started = System.nanoTime();
        List<String> sleeping = List.of("sh", "-c", "sleep 5");

        assertEquals(Either.right(new ExecutedCommand(sleeping)), Exec.launch(dir, sleeping, Duration.ofMillis(200)));
        assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(4)) < 0);
    }

    @Test
    void aLaunchedProgramThatFailsBeforeItSettledSaysWithWhatCode() {
        List<String> failing = List.of("sh", "-c", "exit 3");

        assertEquals(Either.right(new ExecutedCommand(List.of("true"))), Exec.launch(dir, List.of("true"), SETTLE));
        assertEquals(Either.left(new ExecFailure.NonZero(3, "", "")), Exec.launch(dir, failing, SETTLE));
        assertInstanceOf(
                ExecFailure.NotStarted.class,
                Exec.launch(dir, List.of("jazygit-no-such-program"), SETTLE).getLeft());
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
