package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.CommandFailed;
import net.vanfleteren.jazygit.git.model.ConfigValue;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * The configuration, through {@code git config}: what jazygit records in the repository's own, with {@code --local},
 * and the settings of git it follows, as git reads them from the repository's, the user's and the system's.
 */
final class CliConfig implements Config {

    // git config --unset exits with 5 when the key is not set.
    private static final int NOT_SET = 5;
    // The scopes of the repository's own configuration, as git config --show-scope names them.
    private static final List<String> LOCAL_SCOPES = List.of("local", "worktree");
    // How git config --show-origin starts the origin of a value read from a file.
    private static final String FILE = "file:";

    private final Path worktree;

    CliConfig(Path worktree) {
        this.worktree = worktree;
    }

    /**
     * The value of {@code key} in the repository's own configuration, not the user's or the system's. Only looked
     * up, so not logged.
     */
    @Override
    public Either<GitError, Option<String>> local(String key) {
        return readLocal(worktree, key);
    }

    /**
     * The value of {@code key} in the repository's own configuration; the parents of the branches are read through it.
     */
    static Either<GitError, Option<String>> readLocal(Path worktree, String key) {
        return read(worktree, List.of("--local"), key);
    }

    /**
     * The value of {@code key} as git sees it: from the repository's configuration, the user's or the system's,
     * whichever sets it last. Only looked up, so not logged.
     */
    static Either<GitError, Option<String>> effective(Path worktree, String key) {
        return read(worktree, List.empty(), key);
    }

    private static Either<GitError, Option<String>> read(Path worktree, List<String> scope, String key) {
        // git config --get exits with 1, saying nothing, when the key is not set.
        return GitProcess.ask(
                        worktree,
                        GitCommand.reading(List.of("config").appendAll(scope).appendAll(List.of("--get", key))))
                .map(answer -> answer.map(output -> output.stdout().strip()))
                .mapLeft(GitFailure::error);
    }

    /**
     * Every value of {@code key} as git reads them, with where each is. Only looked up, so not logged.
     */
    @Override
    public Either<GitError, List<ConfigValue>> all(String key) {
        // git config --get-all exits with 1, saying nothing, when the key is not set.
        return GitProcess.ask(worktree, GitCommand.reading("config", "--show-scope", "--show-origin", "--get-all", key))
                .map(answer ->
                        answer.map(output -> parseValues(output.stdout())).getOrElse(List.empty()))
                .mapLeft(GitFailure::error);
    }

    /**
     * The values in {@code raw}, as {@code git config --show-scope --show-origin} lists them: the scope, the origin
     * and the value per line, separated by tabs. An origin is a file, after {@code file:}; one that is not, like
     * the command line, is kept as git names it.
     */
    static List<ConfigValue> parseValues(String raw) {
        return GitOutput.nonBlankLines(raw).flatMap(line -> {
            String[] fields = line.split("\t", 3);
            return Option.when(
                    fields.length == 3,
                    () -> new ConfigValue(
                            LOCAL_SCOPES.contains(fields[0]),
                            fields[1].startsWith(FILE) ? fields[1].substring(FILE.length()) : fields[1],
                            fields[2].strip()));
        });
    }

    /**
     * Sets {@code key} to {@code value} in the repository's own configuration, or removes it for none; removing a
     * key that is not set does nothing.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> setLocal(String key, Option<String> value) {
        return writeLocal(worktree, key, value);
    }

    /**
     * Sets {@code key} in the repository's own configuration, or removes it; the parents of the branches are recorded
     * through it.
     */
    static Either<GitFailure, List<ExecutedCommand>> writeLocal(Path worktree, String key, Option<String> value) {
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
