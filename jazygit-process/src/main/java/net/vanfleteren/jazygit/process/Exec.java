package net.vanfleteren.jazygit.process;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

/**
 * Runs a command line program and waits for it. The only place that starts processes.
 */
public final class Exec {

    // How git starts the lines that say what went wrong.
    private static final List<String> ERROR_PREFIXES = List.of("fatal:", "error:", "CONFLICT");
    // What ran, for the debug log; never the environment, which may hold a token.
    private static final Logger LOG = LoggerFactory.getLogger(Exec.class);

    private Exec() {}

    /**
     * Runs {@code commandLine} in {@code directory}, with {@code environment} set on top of the inherited one.
     *
     * @return what it printed on its standard output, when it exited with 0
     */
    public static Either<ExecFailure, String> run(
            Path directory, List<String> commandLine, Map<String, String> environment, Duration timeout) {
        long started = System.nanoTime();
        return logged(directory, commandLine, started, execute(directory, commandLine, environment, timeout));
    }

    private static Either<ExecFailure, String> execute(
            Path directory, List<String> commandLine, Map<String, String> environment, Duration timeout) {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try {
            ProcessResult result = new ProcessExecutor()
                    .command(commandLine.toJavaList())
                    .directory(directory.toFile())
                    .environment(environment.toJavaMap())
                    .readOutput(true)
                    .redirectError(stderr)
                    .exitValueAny()
                    .timeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .execute();
            return result.getExitValue() == 0
                    ? Either.right(result.outputUTF8())
                    : Either.left(new ExecFailure.NonZero(
                            result.getExitValue(), stderr.toString(StandardCharsets.UTF_8), result.outputUTF8()));
        } catch (IOException e) {
            return Either.left(new ExecFailure.NotStarted(e));
        } catch (TimeoutException e) {
            return Either.left(new ExecFailure.TimedOut());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Either.left(new ExecFailure.Interrupted());
        }
    }

    /**
     * Runs {@code commandLine} in {@code directory} on the terminal of this process, for the user to work with,
     * like an editor, and waits for it however long that takes. What it prints goes to the terminal, so a
     * {@link ExecFailure.NonZero} has no output.
     *
     * @return the command as it was run, when it exited with 0
     */
    public static Either<ExecFailure, ExecutedCommand> interactive(Path directory, List<String> commandLine) {
        long started = System.nanoTime();
        return logged(directory, commandLine, started, handOver(directory, commandLine));
    }

    private static Either<ExecFailure, ExecutedCommand> handOver(Path directory, List<String> commandLine) {
        try {
            Process process = new ProcessBuilder(commandLine.toJavaList())
                    .directory(directory.toFile())
                    .inheritIO()
                    .start();
            int exitCode = process.waitFor();
            return exitCode == 0
                    ? Either.right(new ExecutedCommand(commandLine))
                    : Either.left(new ExecFailure.NonZero(exitCode, "", ""));
        } catch (IOException e) {
            return Either.left(new ExecFailure.NotStarted(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Either.left(new ExecFailure.Interrupted());
        }
    }

    /**
     * Writes {@code commandLine}, which started at {@code started} in {@code directory}, to the debug log with how
     * it ended in {@code result}.
     */
    private static <T> Either<ExecFailure, T> logged(
            Path directory, List<String> commandLine, long started, Either<ExecFailure, T> result) {
        if (LOG.isDebugEnabled()) {
            LOG.debug(
                    "{} in {}, {} ms: {}",
                    CommandLine.format(commandLine),
                    directory,
                    Duration.ofNanos(System.nanoTime() - started).toMillis(),
                    result.fold(Exec::outcome, done -> "ok"));
        }
        return result;
    }

    private static String outcome(ExecFailure failure) {
        return switch (failure) {
            case ExecFailure.NonZero(int exitCode, String stderr, String stdout) ->
                "exit " + exitCode + (stderr.isBlank() ? "" : System.lineSeparator() + stderr.strip());
            case ExecFailure.NotStarted(IOException cause) -> "not started: " + cause.getMessage();
            case ExecFailure.TimedOut() -> "timed out";
            case ExecFailure.Interrupted() -> "interrupted";
        };
    }

    /**
     * The line of what a program printed that states why it failed: the first one that says it is an error,
     * like {@code error: failed to push some refs}, as progress or hints may come before it; else the first
     * non-blank one. Its error output is looked at first.
     */
    public static Option<String> explanation(String stderr, String stdout) {
        List<String> lines = lines(stderr).appendAll(lines(stdout));
        return lines.find(line -> ERROR_PREFIXES.exists(line::startsWith)).orElse(lines::headOption);
    }

    private static List<String> lines(String output) {
        return List.of(output.split("\\R")).map(String::strip).filter(line -> !line.isEmpty());
    }
}
