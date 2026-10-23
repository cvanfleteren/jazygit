package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.CommandFailed;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * The configuration of the repository itself, through {@code git config --local}.
 */
final class CliConfig {

    // git config --unset exits with 5 when the key is not set.
    private static final int NOT_SET = 5;

    private CliConfig() {}

    /**
     * The branch {@code branch} was made from, as {@code gh} records it to take as the base of its pull request.
     */
    static Either<ProcessError, Option<BranchName>> parent(Path worktree, BranchName branch) {
        return get(worktree, parentKey(branch)).map(value -> value.flatMap(BranchName::parse));
    }

    /**
     * Records {@code parent} as the branch {@code branch} was made from, where {@code gh} looks for it.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> setParent(
            Path worktree, BranchName branch, BranchName parent) {
        return set(worktree, parentKey(branch), Option.of(parent.value()));
    }

    private static String parentKey(BranchName branch) {
        return "branch." + branch.value() + ".gh-merge-base";
    }

    /**
     * The value of {@code key} in the repository's own configuration, not the user's or the system's. Only looked
     * up, so not logged.
     */
    static Either<ProcessError, Option<String>> get(Path worktree, String key) {
        // git config --get exits with 1, saying nothing, when the key is not set.
        return GitProcess.ask(worktree, GitCommand.reading("config", "--local", "--get", key))
                .map(answer -> answer.map(output -> output.stdout().strip()))
                .mapLeft(GitFailure::error);
    }

    /**
     * Sets {@code key} to {@code value} in the repository's own configuration, or removes it for none; removing a
     * key that is not set does nothing.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> set(
            Path worktree, String key, Option<String> value) {
        return value.fold(
                () -> {
                    GitCommand unset = git("config", "--local", "--unset-all", key);
                    return GitProcess.log(worktree, unset)
                            .fold(
                                    failure -> failure.error() instanceof CommandFailed failed
                                                    && failed.exitCode() == NOT_SET
                                            ? Either.right(List.of(unset.executed()))
                                            : Either.left(failure),
                                    Either::right);
                },
                set -> GitProcess.log(worktree, git("config", "--local", key, set)));
    }
}
