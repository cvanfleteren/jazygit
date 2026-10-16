package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.CommandFailed;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * The configuration of the repository itself, through {@code git config --local}.
 */
final class CliConfig {

    // git config --unset exits with 5 when the key is not set.
    private static final int NOT_SET = 5;

    private CliConfig() {}

    /**
     * The value of {@code key} in the repository's own configuration, not the user's or the system's. Only looked
     * up, so not logged.
     */
    static Either<ProcessError, Option<String>> get(Path workTree, String key) {
        // git config --get exits with 1, saying nothing, when the key is not set.
        return GitProcess.ask(workTree, GitCommand.reading("config", "--local", "--get", key))
                .map(answer -> answer.map(output -> output.stdout().strip()))
                .mapLeft(GitFailure::error);
    }

    /**
     * Sets {@code key} to {@code value} in the repository's own configuration, or removes it for none; removing a
     * key that is not set does nothing.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> set(
            Path workTree, String key, Option<String> value) {
        return value.fold(
                () -> {
                    GitCommand unset = git("config", "--local", "--unset-all", key);
                    return GitProcess.log(workTree, unset)
                            .fold(
                                    failure -> failure.error() instanceof CommandFailed failed
                                                    && failed.exitCode() == NOT_SET
                                            ? Either.right(List.of(unset.executed()))
                                            : Either.left(failure),
                                    Either::right);
                },
                set -> GitProcess.log(workTree, git("config", "--local", key, set)));
    }
}
