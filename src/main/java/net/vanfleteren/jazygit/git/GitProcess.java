package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.CommandFailed;
import net.vanfleteren.jazygit.git.GitError.GitUnavailable;
import net.vanfleteren.jazygit.git.GitError.Interrupted;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.TimedOut;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs {@link GitCommand}s through the {@code git} command line tool. The only place that starts git.
 */
final class GitProcess {

    private GitProcess() {
    }

    /**
     * What a command that succeeded printed on its standard output, and the line it was run as.
     */
    record Output(String stdout, String line) {

        /**
         * The command log of this one command.
         */
        List<String> log() {
            return List.of(line);
        }
    }

    /**
     * Runs {@code command} in {@code workTree}. A non-zero exit code is a {@link CommandFailed}.
     */
    static Either<GitFailure<ProcessError>, Output> run(Path workTree, GitCommand command) {
        return execute(workTree, command).mapLeft(error -> new GitFailure<>(error, List.of(command.line())));
    }

    /**
     * Runs {@code command} for its effect only, as a clean-up whose own failure changes nothing, and
     * returns the command log either way.
     */
    static List<String> attempt(Path workTree, GitCommand command) {
        return run(workTree, command).fold(GitFailure::commands, Output::log);
    }

    private static Either<ProcessError, Output> execute(Path workTree, GitCommand command) {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try {
            ProcessResult result = new ProcessExecutor()
                    .command(command.commandLine().toJavaList())
                    .directory(workTree.toFile())
                    .environment(command.environment().toJavaMap())
                    .readOutput(true)
                    .redirectError(stderr)
                    .exitValueAny()
                    .timeout(command.timeout().toMillis(), TimeUnit.MILLISECONDS)
                    .execute();
            return result.getExitValue() == 0
                    ? Either.right(new Output(result.outputUTF8(), command.line()))
                    : Either.left(new CommandFailed(
                    explanation(stderr.toString(StandardCharsets.UTF_8), result.outputUTF8()),
                    result.getExitValue()));
        } catch (IOException e) {
            return Either.left(new GitUnavailable());
        } catch (TimeoutException e) {
            return Either.left(new TimedOut(command.operation(), workTree));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Either.left(new Interrupted(command.operation()));
        }
    }

    /**
     * The first line git printed, which states why it failed, e.g. {@code error: Your local changes to
     * the following files would be overwritten by checkout:}. Its error output is looked at first.
     */
    static String explanation(String stderr, String stdout) {
        return firstLine(stderr).orElse(() -> firstLine(stdout)).getOrElse("");
    }

    private static Option<String> firstLine(String output) {
        return List.ofAll(output.lines()).map(String::strip).find(line -> !line.isEmpty());
    }
}
