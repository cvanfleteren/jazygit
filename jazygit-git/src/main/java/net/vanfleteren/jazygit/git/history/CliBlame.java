package net.vanfleteren.jazygit.git.history;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.Blame;
import net.vanfleteren.jazygit.git.model.BlameAt;
import net.vanfleteren.jazygit.git.model.BlameOptions;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.process.GitArgs;
import net.vanfleteren.jazygit.git.process.GitCommand;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.git.process.GitOutput;
import net.vanfleteren.jazygit.git.process.GitProcess;
import net.vanfleteren.jazygit.git.process.TempFiles;

/**
 * Blame on a version of a file, through {@code git blame --porcelain} (ADR 147). A read is stopped after the 30
 * seconds of any read, and then failed.
 */
final class CliBlame {

    private CliBlame() {}

    /**
     * Who last changed each line of the file {@code at} names, blamed with {@code options}. The index is written to a
     * temporary directory and blamed with {@code --contents <file> HEAD}, as jazygit gives git no standard input.
     */
    static Either<GitError, Blame> blame(Path worktree, BlameAt at, BlameOptions options) {
        return switch (at) {
            case BlameAt.InCommit(Sha sha, int parent, String path) ->
                read(worktree, options, List.of(GitArgs.parent(new Revision.Commit(sha), parent)), path);
            case BlameAt.AtMergeBase(Sha base, Sha tip, String path) ->
                mergeBase(worktree, base, tip)
                        .flatMap(fork ->
                                read(worktree, options, List.of(GitArgs.revision(new Revision.Commit(fork))), path));
            case BlameAt.OnDisk(String path) -> read(worktree, options, List.empty(), path);
            case BlameAt.InIndex(String path) ->
                TempFiles.usingDirectory(
                        "jazygit-blame",
                        failed -> failed,
                        directory -> staged(worktree, directory, path)
                                .flatMap(file -> read(
                                        worktree,
                                        options,
                                        List.of("--contents", file.toString(), GitArgs.revision(new Revision.Head())),
                                        path)));
        };
    }

    /**
     * The file staged at {@code path}, written by git into {@code directory} as it would be checked out. Not read
     * through jazygit: a file that is not UTF-8 would not be written back byte for byte, and its lines would then
     * differ from {@code HEAD}. {@code --contents} converts it back as git stores it.
     */
    private static Either<GitError, Path> staged(Path worktree, Path directory, String path) {
        return GitProcess.read(
                        worktree,
                        GitCommand.reading(
                                        "checkout-index",
                                        "--ignore-skip-worktree-bits",
                                        "--prefix=" + directory + "/",
                                        "--",
                                        path)
                                .withLiteralPaths(),
                        Either::right)
                .map(written -> directory.resolve(path));
    }

    /**
     * The flags of {@code git blame} that blame with {@code options}.
     */
    static List<String> flags(BlameOptions options) {
        return List.of("--porcelain", "-M")
                .appendAll(options.ignoreWhitespace() ? List.of("-w") : List.empty())
                .appendAll(options.lastLine()
                        .map(last -> List.of("-L", "1," + Math.max(1, last)))
                        .getOrElse(List.empty()));
    }

    private static Either<GitError, Blame> read(Path worktree, BlameOptions options, List<String> at, String path) {
        return GitProcess.read(
                worktree,
                GitCommand.reading(List.of("blame")
                                .appendAll(flags(options))
                                .appendAll(at)
                                .append("--")
                                .append(path))
                        .withLiteralPaths(),
                raw -> Blame.parse(raw).toEither(() -> new GitError.UnexpectedOutput(raw)));
    }

    private static Either<GitError, Sha> mergeBase(Path worktree, Sha base, Sha tip) {
        return GitProcess.read(worktree, GitCommand.reading("merge-base", base.value(), tip.value()), GitOutput::sha);
    }
}
