package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.reading;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.FileEntry;

/**
 * Reads the diff of changed files through the git command line tool.
 */
final class CliDiff {

    static final int MAX_LINES = 5000;
    /**
     * The flags that keep git's own {@code a/} and {@code b/} before the paths of a diff that is parsed
     * ({@code UnifiedDiff}), whatever the user's {@code diff.noprefix}, {@code diff.mnemonicPrefix},
     * {@code diff.srcPrefix} or {@code diff.dstPrefix} say.
     */
    static final List<String> PREFIXES = List.of("--src-prefix=a/", "--dst-prefix=b/");

    private static final List<String> STAGED =
            List.of("diff", "--cached", "--no-color", "--no-ext-diff").appendAll(PREFIXES);
    private static final List<String> UNSTAGED =
            List.of("diff", "--no-color", "--no-ext-diff").appendAll(PREFIXES);

    private CliDiff() {}

    /**
     * The staged changes of the files (index against HEAD), and their unstaged changes (working tree
     * against the index), read with {@code options}. Untracked files are shown as entirely added. Long diffs
     * are cut off.
     */
    static Either<GitError, Diffs> diff(Path worktree, List<FileEntry> files, DiffOptions options) {
        // A rename only shows as one when both of its paths are diffed.
        List<String> staged = files.filter(f -> f.staged() && f.type() != ChangeType.UNTRACKED)
                .flatMap(f -> f.paths(true));
        List<String> unstaged = files.filter(f -> f.unstaged() && f.type() != ChangeType.UNTRACKED)
                .flatMap(f -> f.paths(false));
        List<String> untracked = FileEntry.paths(files, f -> f.type() == ChangeType.UNTRACKED);
        List<String> unstagedDiff = UNSTAGED.appendAll(flags(options)).append("--");
        return Either.<GitError, String>narrow(
                        tracked(worktree, STAGED.appendAll(flags(options)).append("--"), staged, HashMap.empty()))
                .flatMap(stagedDiff -> unstagedAndUntracked(worktree, unstagedDiff, unstaged, untracked)
                        .map(unstagedText -> new Diffs(truncated(stagedDiff), truncated(unstagedText))));
    }

    /**
     * The flags of {@code git diff} and {@code git show} that read diffs with {@code options}.
     */
    static List<String> flags(DiffOptions options) {
        return options.ignoreWhitespace() ? List.of("--ignore-all-space") : List.empty();
    }

    /**
     * Untracked files are marked intent-to-add in a temporary copy of the index, so one {@code git diff}
     * shows them as entirely added next to the unstaged changes. The real index is left untouched.
     */
    private static Either<GitError, String> unstagedAndUntracked(
            Path worktree, List<String> subcommand, List<String> unstaged, List<String> untracked) {
        return untracked.isEmpty()
                ? Either.narrow(tracked(worktree, subcommand, unstaged, HashMap.empty()))
                : Either.<GitError, String>narrow(
                                run(worktree, List.of("rev-parse", "--git-path", "index"), HashMap.empty()))
                        .map(index -> worktree.resolve(index.strip()))
                        .flatMap(index -> TempFiles.using(
                                "jazygit-index",
                                copy -> copyIndex(index, copy),
                                failed -> failed,
                                copy -> withUntracked(
                                        worktree,
                                        subcommand,
                                        unstaged,
                                        untracked,
                                        HashMap.of("GIT_INDEX_FILE", copy.toString()))));
    }

    /**
     * Copies the {@code index} file, keeping its modification time: that keeps git's check for files changed
     * within the same second as the index working on the copy. Without an index yet, as before anything was
     * added, there is no copy either, which git takes as an empty index; an empty file it would refuse.
     */
    private static void copyIndex(Path index, Path copy) throws IOException {
        if (Files.exists(index)) {
            Files.copy(index, copy, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        } else {
            Files.delete(copy);
        }
    }

    private static Either<ProcessError, String> withUntracked(
            Path worktree,
            List<String> subcommand,
            List<String> unstaged,
            List<String> untracked,
            Map<String, String> env) {
        return run(worktree, List.of("add", "--intent-to-add", "--").appendAll(untracked), env)
                .flatMap(added -> tracked(worktree, subcommand, unstaged.appendAll(untracked), env));
    }

    private static Either<ProcessError, String> tracked(
            Path worktree, List<String> subcommand, List<String> paths, Map<String, String> env) {
        return paths.isEmpty() ? Either.right("") : run(worktree, subcommand.appendAll(paths), env);
    }

    /**
     * {@code diff} cut off after {@link #MAX_LINES} lines.
     */
    static DiffText truncated(String diff) {
        List<String> lines = GitOutput.lines(diff);
        return lines.size() <= MAX_LINES
                ? DiffText.of(diff)
                : new DiffText(lines.take(MAX_LINES).mkString("", "\n", "\n"), true);
    }

    private static Either<ProcessError, String> run(Path worktree, List<String> args, Map<String, String> env) {
        return GitProcess.read(worktree, reading(args).withEnvironment(env).withLiteralPaths(), Either::right);
    }
}
