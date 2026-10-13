package net.vanfleteren.jazygit.process;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs a command line program and waits for it. The only place that starts processes.
 */
public final class Exec {

    private Exec() {
    }

    /**
     * Runs {@code commandLine} in {@code directory}, with {@code environment} set on top of the inherited one.
     *
     * @return what it printed on its standard output, when it exited with 0
     */
    public static Either<ExecFailure, String> run(Path directory, List<String> commandLine,
                                                  Map<String, String> environment, Duration timeout) {
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
                    : Either.left(new ExecFailure.NonZero(result.getExitValue(),
                    stderr.toString(StandardCharsets.UTF_8), result.outputUTF8()));
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
     * The first non-blank line a program printed, which usually states why it failed. Its error output is
     * looked at first.
     */
    public static Option<String> explanation(String stderr, String stdout) {
        return firstLine(stderr).orElse(() -> firstLine(stdout));
    }

    private static Option<String> firstLine(String output) {
        return List.of(output.split("\\R")).map(String::strip).find(line -> !line.isEmpty());
    }
}
