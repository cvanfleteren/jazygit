package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.CommandFailed;
import net.vanfleteren.jazygit.git.GitError.GitUnavailable;
import net.vanfleteren.jazygit.git.GitError.Interrupted;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.TimedOut;
import net.vanfleteren.jazygit.git.GitError.UnexpectedOutput;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Runs {@link GitCommand}s through the {@code git} command line tool. The only place that starts git.
 */
final class GitProcess {

    private GitProcess() {
    }

    /**
     * What a command that succeeded printed on its standard output, and the line it was run as.
     */
    record Output(String stdout, ExecutedCommand command) {

        /**
         * The command log of this one command.
         */
        List<ExecutedCommand> log() {
            return List.of(command);
        }
    }

    /**
     * Runs {@code command} in {@code workTree}. A non-zero exit code is a {@link CommandFailed}.
     */
    static Either<GitFailure<ProcessError>, Output> run(Path workTree, GitCommand command) {
        return execute(workTree, command).mapLeft(error -> new GitFailure<>(error, List.of(command.executed())));
    }

    /**
     * Runs {@code command} for its effect, and returns the command log.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> log(Path workTree, GitCommand command) {
        return run(workTree, command).map(Output::log);
    }

    /**
     * Runs {@code command} to read something, which {@code parse} makes of its output. Reads are not logged.
     */
    static <T> Either<ProcessError, T> read(Path workTree, GitCommand command,
                                            Function<String, Either<UnexpectedOutput, T>> parse) {
        return execute(workTree, command).flatMap(output -> Either.<ProcessError, T>narrow(parse.apply(output.stdout())));
    }

    /**
     * Runs {@code command}, a question git answers "no" to by exiting with 1 without saying anything, like
     * {@code git diff --quiet} or {@code git config --get} for a key that is not set. That answer is none.
     */
    static Either<GitFailure<ProcessError>, Option<Output>> ask(Path workTree, GitCommand command) {
        return run(workTree, command).fold(
                failure -> failure.error() instanceof CommandFailed failed && failed.silent()
                        ? Either.right(Option.none())
                        : Either.left(failure),
                output -> Either.right(Option.of(output)));
    }

    /**
     * Runs {@code command}, and {@code undo} when it fails and {@code needsUndo} says so. The undo's commands
     * join the failure's, and so does why the undo failed, if it did.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> runOrUndo(Path workTree, GitCommand command, GitCommand undo,
                                                                  BooleanSupplier needsUndo) {
        return log(workTree, command)
                .mapLeft(failure -> needsUndo.getAsBoolean() ? failure.followedBy(log(workTree, undo)) : failure);
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
                    ? Either.right(new Output(result.outputUTF8(), command.executed()))
                    : Either.left(new CommandFailed(
                    explanation(stderr.toString(StandardCharsets.UTF_8), result.outputUTF8()),
                    result.getExitValue()));
        } catch (IOException e) {
            return Either.left(new GitUnavailable(e));
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
    static Option<String> explanation(String stderr, String stdout) {
        return firstLine(stderr).orElse(() -> firstLine(stdout));
    }

    private static Option<String> firstLine(String output) {
        return GitOutput.lines(output).map(String::strip).find(line -> !line.isEmpty());
    }
}
