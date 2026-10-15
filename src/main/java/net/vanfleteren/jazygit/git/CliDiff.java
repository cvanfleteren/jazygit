package net.vanfleteren.jazygit.git;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.DiffError;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Diffs;
import net.vanfleteren.jazygit.git.model.FileEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static net.vanfleteren.jazygit.git.GitCommand.reading;

/**
 * Reads the diff of changed files through the git command line tool.
 */
final class CliDiff {

    static final int MAX_LINES = 5000;
    private static final List<String> STAGED = List.of("diff", "--cached", "--no-color", "--no-ext-diff", "--");
    private static final List<String> UNSTAGED = List.of("diff", "--no-color", "--no-ext-diff", "--");

    private CliDiff() {
    }

    /**
     * The staged changes of the files (index against HEAD), and their unstaged changes (working tree
     * against the index). Untracked files are shown as entirely added. Long diffs are cut off.
     */
    static Either<DiffError, Diffs> diff(Path workTree, List<FileEntry> files) {
        // A rename only shows as one when both of its paths are diffed.
        List<String> staged = files.filter(f -> f.staged() && f.type() != ChangeType.UNTRACKED)
                .flatMap(f -> f.paths(true));
        List<String> unstaged = files.filter(f -> f.unstaged() && f.type() != ChangeType.UNTRACKED)
                .flatMap(f -> f.paths(false));
        List<String> untracked = FileEntry.paths(files, f -> f.type() == ChangeType.UNTRACKED);
        return Either.<DiffError, String>narrow(tracked(workTree, STAGED, staged, HashMap.empty()))
                .flatMap(stagedDiff -> unstagedAndUntracked(workTree, unstaged, untracked)
                        .map(unstagedDiff -> new Diffs(truncated(stagedDiff), truncated(unstagedDiff))));
    }


    /**
     * Untracked files are marked intent-to-add in a temporary copy of the index, so one {@code git diff}
     * shows them as entirely added next to the unstaged changes. The real index is left untouched.
     */
    private static Either<DiffError, String> unstagedAndUntracked(Path workTree, List<String> unstaged,
                                                                  List<String> untracked) {
        return untracked.isEmpty()
                ? Either.narrow(tracked(workTree, UNSTAGED, unstaged, HashMap.empty()))
                : Either.<DiffError, String>narrow(run(workTree, List.of("rev-parse", "--git-path", "index"), HashMap.empty()))
                .map(index -> workTree.resolve(index.strip()))
                .flatMap(index -> TempFiles.using("jazygit-index", copy -> copyIndex(index, copy), failed -> failed,
                        copy -> withUntracked(workTree, unstaged, untracked,
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

    private static Either<ProcessError, String> withUntracked(Path workTree, List<String> unstaged,
                                                              List<String> untracked, Map<String, String> env) {
        return run(workTree, List.of("add", "--intent-to-add", "--").appendAll(untracked), env)
                .flatMap(added -> tracked(workTree, UNSTAGED, unstaged.appendAll(untracked), env));
    }

    private static Either<ProcessError, String> tracked(Path workTree, List<String> subcommand, List<String> paths,
                                                        Map<String, String> env) {
        return paths.isEmpty() ? Either.right("") : run(workTree, subcommand.appendAll(paths), env);
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

    private static Either<ProcessError, String> run(Path workTree, List<String> args, Map<String, String> env) {
        return GitProcess.read(workTree, reading(args).withEnvironment(env).withLiteralPaths(), Either::right);
    }
}
