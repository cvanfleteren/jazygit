package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.CommandFailed;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * The configuration, through {@code git config}: what jazygit records in the repository's own, with {@code --local},
 * and the settings of git it follows, as git reads them from the repository's, the user's and the system's.
 */
final class CliConfig {

    // git config --unset exits with 5 when the key is not set.
    private static final int NOT_SET = 5;
    private static final String PARENT_PREFIX = "branch.";
    private static final String PARENT_SUFFIX = ".gh-merge-base";

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
        return set(worktree, parentKey(branch), Option.of(GitArgs.name(parent)));
    }

    private static String parentKey(BranchName branch) {
        return PARENT_PREFIX + GitArgs.name(branch) + PARENT_SUFFIX;
    }

    /**
     * The parent of each branch that has one recorded, as {@code gh} does, in one {@code git config}.
     */
    static Either<ProcessError, Map<BranchName, BranchName>> parents(Path worktree) {
        // git config --get-regexp exits with 1, saying nothing, when no key matches.
        return GitProcess.ask(
                        worktree,
                        GitCommand.reading(
                                "config",
                                "--local",
                                "--get-regexp",
                                "^" + PARENT_PREFIX.replace(".", "\\.") + ".*" + PARENT_SUFFIX.replace(".", "\\.")
                                        + "$"))
                .mapLeft(GitFailure::error)
                .map(answer ->
                        answer.map(output -> parseParents(output.stdout())).getOrElse(HashMap.empty()));
    }

    /**
     * The parents in {@code raw}, as {@code git config --get-regexp} lists them: the key, a space and the value per
     * line. A value that is no branch name is left out.
     */
    static Map<BranchName, BranchName> parseParents(String raw) {
        return HashMap.ofEntries(GitOutput.nonBlankLines(raw).flatMap(line -> {
            int space = line.indexOf(' ');
            String key = space < 0 ? line : line.substring(0, space);
            Option<BranchName> branch = Option.when(
                            key.startsWith(PARENT_PREFIX)
                                    && key.endsWith(PARENT_SUFFIX)
                                    && key.length() > PARENT_PREFIX.length() + PARENT_SUFFIX.length(),
                            () -> key.substring(PARENT_PREFIX.length(), key.length() - PARENT_SUFFIX.length()))
                    .map(BranchName::new);
            Option<BranchName> parent =
                    Option.when(space >= 0, () -> line.substring(space + 1)).flatMap(BranchName::parse);
            return branch.flatMap(name -> parent.map(value -> Tuple.of(name, value)));
        }));
    }

    /**
     * The value of {@code key} in the repository's own configuration, not the user's or the system's. Only looked
     * up, so not logged.
     */
    static Either<ProcessError, Option<String>> get(Path worktree, String key) {
        return read(worktree, List.of("--local"), key);
    }

    /**
     * The value of {@code key} as git sees it: from the repository's configuration, the user's or the system's,
     * whichever sets it last. Only looked up, so not logged.
     */
    static Either<ProcessError, Option<String>> effective(Path worktree, String key) {
        return read(worktree, List.empty(), key);
    }

    private static Either<ProcessError, Option<String>> read(Path worktree, List<String> scope, String key) {
        // git config --get exits with 1, saying nothing, when the key is not set.
        return GitProcess.ask(
                        worktree,
                        GitCommand.reading(List.of("config").appendAll(scope).appendAll(List.of("--get", key))))
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
