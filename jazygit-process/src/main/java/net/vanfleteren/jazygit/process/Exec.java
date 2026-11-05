package net.vanfleteren.jazygit.process;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.listener.ProcessListener;

/**
 * Runs a command line program and waits for it. The only place that starts processes.
 */
public final class Exec {

    // The most of the error output that is kept: what went wrong is said at its start (ADR 79).
    static final int MAX_STDERR = 64 * 1024;
    // The most of any output that is read, which is far more than is ever meant to be (ADR 79).
    static final Limit MAX_OUTPUT = new Limit(Integer.MAX_VALUE, 64 * 1024 * 1024);
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
        return logged(
                directory,
                commandLine,
                started,
                execute(directory, commandLine, environment, timeout, MAX_OUTPUT, false)
                        .flatMap(output -> output.cut()
                                ? Either.left(new ExecFailure.TooMuchOutput())
                                : Either.right(output.text())));
    }

    /**
     * Runs {@code commandLine} as {@link #run} does, reading its standard output only up to {@code limit}: once it
     * printed more, it is stopped, and what it printed is cut off, however it exited. When it takes too long, the
     * whole lines it printed until then are what it printed, cut off, unless there are none (ADR 79).
     *
     * @return what it printed, when it exited with 0 or was cut off
     */
    public static Either<ExecFailure, Limited> read(
            Path directory, List<String> commandLine, Map<String, String> environment, Duration timeout, Limit limit) {
        long started = System.nanoTime();
        return logged(
                directory, commandLine, started, execute(directory, commandLine, environment, timeout, limit, true));
    }

    /**
     * Runs {@code commandLine} as {@link #run} does, reading its standard output as bytes, not text, up to
     * {@code maxBytes}: once it printed more, it is stopped, and that is a failure, {@link ExecFailure.TooMuchOutput}.
     *
     * @return what it printed, when it exited with 0
     */
    public static Either<ExecFailure, byte[]> readBytes(
            Path directory, List<String> commandLine, Map<String, String> environment, Duration timeout, int maxBytes) {
        long started = System.nanoTime();
        FirstBytes stdout = new FirstBytes(maxBytes);
        return logged(
                directory,
                commandLine,
                started,
                started(directory, commandLine, environment, timeout, stdout, stdout::whenOver)
                        .flatMap(exited -> stdout.over()
                                ? Either.left(new ExecFailure.TooMuchOutput())
                                : exited.exitCode() == 0
                                        ? Either.right(stdout.toByteArray())
                                        : Either.left(
                                                new ExecFailure.NonZero(exited.exitCode(), exited.stderr(), ""))));
    }

    /**
     * Runs {@code commandLine}, reading its output up to {@code limit}; when it takes too long, what it printed until
     * then is kept, if {@code keepWhenLate}.
     */
    private static Either<ExecFailure, Limited> execute(
            Path directory,
            List<String> commandLine,
            Map<String, String> environment,
            Duration timeout,
            Limit limit,
            boolean keepWhenLate) {
        CutOutput stdout = new CutOutput(limit);
        return started(directory, commandLine, environment, timeout, stdout, stdout::whenOver)
                .fold(
                        failure -> {
                            Limited printed = stdout.wholeLines();
                            return failure instanceof ExecFailure.TimedOut
                                            && keepWhenLate
                                            && !printed.text().isEmpty()
                                    ? Either.right(printed)
                                    : Either.left(failure);
                        },
                        exited -> {
                            Limited output = stdout.limited();
                            return output.cut() || exited.exitCode() == 0
                                    ? Either.right(output)
                                    : Either.left(
                                            new ExecFailure.NonZero(exited.exitCode(), exited.stderr(), output.text()));
                        });
    }

    /**
     * Runs {@code commandLine}, its standard output going to {@code stdout}, which is handed what stops it, to stop it
     * when it printed too much; how it exited, with the start of its error output.
     */
    private static Either<ExecFailure, Exited> started(
            Path directory,
            List<String> commandLine,
            Map<String, String> environment,
            Duration timeout,
            OutputStream stdout,
            Consumer<Runnable> whenOver) {
        FirstBytes stderr = new FirstBytes(MAX_STDERR);
        try {
            ProcessResult result = new ProcessExecutor()
                    .command(commandLine.toJavaList())
                    .directory(directory.toFile())
                    .environment(environment.toJavaMap())
                    .redirectOutput(stdout)
                    .redirectError(stderr)
                    .addListener(new ProcessListener() {
                        @Override
                        public void afterStart(Process process, ProcessExecutor executor) {
                            whenOver.accept(() -> stop(process));
                        }
                    })
                    .exitValueAny()
                    .timeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .execute();
            return Either.right(new Exited(result.getExitValue(), stderr.toString(StandardCharsets.UTF_8)));
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
     * Stops {@code process}, and what it started, which may hold its output open.
     */
    private static void stop(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
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
            case ExecFailure.TooMuchOutput() -> "too much output";
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

    /**
     * How a program exited: its exit code, and the start of its error output.
     */
    private record Exited(int exitCode, String stderr) {}
}
