package net.vanfleteren.jazygit.process;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
    void bytesAreReadAsTheyArePrintedNotAsText() {
        Either<ExecFailure, byte[]> read = Exec.readBytes(
                dir, List.of("printf", "\\211PNG\\000\\377"), HashMap.empty(), Duration.ofSeconds(30), 1 << 20);

        assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, (byte) 0xff}, read.get());
    }

    @Test
    void bytesPastTheLimitAreTooMuchOutputAndStopTheProgram() {
        long started = System.nanoTime();

        Either<ExecFailure, byte[]> read =
                Exec.readBytes(dir, List.of("yes"), HashMap.empty(), Duration.ofSeconds(30), 1000);

        assertEquals(new ExecFailure.TooMuchOutput(), read.getLeft());
        assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(10)) < 0);
        assertEquals(
                3,
                Exec.readBytes(dir, List.of("printf", "abc"), HashMap.empty(), Duration.ofSeconds(30), 3)
                        .get()
                        .length);
    }

    @Test
    void aProgramThatFailsWhileBytesAreReadKeepsItsErrorOutput() {
        assertEquals(
                Either.left(new ExecFailure.NonZero(2, "nope\n", "")),
                Exec.readBytes(
                                dir,
                                List.of("sh", "-c", "echo nope >&2; exit 2"),
                                HashMap.empty(),
                                Duration.ofSeconds(30),
                                100)
                        .map(bytes -> bytes.length));
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
