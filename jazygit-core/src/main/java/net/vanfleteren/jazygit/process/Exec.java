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
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

/**
 * Runs a command line program and waits for it. The only place that starts processes.
 */
public final class Exec {

    // How git starts the lines that say what went wrong.
    private static final List<String> ERROR_PREFIXES = List.of("fatal:", "error:", "CONFLICT");

    private Exec() {}

    /**
     * Runs {@code commandLine} in {@code directory}, with {@code environment} set on top of the inherited one.
     *
     * @return what it printed on its standard output, when it exited with 0
     */
    public static Either<ExecFailure, String> run(
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
