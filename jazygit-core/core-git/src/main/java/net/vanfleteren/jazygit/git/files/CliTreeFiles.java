package net.vanfleteren.jazygit.git.files;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Path;
import java.time.Duration;
import net.vanfleteren.jazygit.git.model.FileContent;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.process.GitCommand;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.git.process.GitProcess;
import net.vanfleteren.jazygit.process.Limit;
import net.vanfleteren.jazygit.process.Limited;

/**
 * All files of a commit, and what one of them holds in it (ADR 128): the files of a commit list them with the files it
 * changed, and show those it did not change as they are in it. They are read from the object database, whatever is
 * checked out.
 */
final class CliTreeFiles {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    // The modes git gives a symbolic link and a submodule in a tree.
    private static final String LINK = "120000";
    private static final String SUBMODULE = "160000";

    private CliTreeFiles() {}

    /**
     * The paths of all files of the tree of {@code sha}, sorted: a submodule is one entry, a symbolic link one file.
     */
    static Either<GitError, List<String>> files(Path worktree, Sha sha) {
        return GitProcess.read(
                worktree,
                GitCommand.reading("ls-tree", "-r", "-z", "--full-tree", "--name-only", sha.value())
                        .withTimeout(TIMEOUT),
                raw -> Either.right(
                        List.of(raw.split("\0")).filter(path -> !path.isEmpty()).sorted()));
    }

    /**
     * The text of the file at {@code path} in the commit {@code sha}, as {@code git show <sha>:<path>} prints it.
     */
    static Either<GitError, String> text(Path worktree, Sha sha, String path) {
        return GitProcess.read(
                worktree, GitCommand.reading("show", sha.value() + ":" + path).withTimeout(TIMEOUT), Either::right);
    }

    /**
     * What the file at {@code path} holds in the commit {@code sha}: a link, a submodule, a binary file, or text up to
     * {@code limit}.
     */
    static Either<GitError, FileContent> content(Path worktree, Sha sha, String path, Limit limit) {
        return entry(worktree, sha, path)
                .flatMap(found -> found.map(entry -> switch (entry.mode()) {
                            case LINK ->
                                blob(worktree, entry.object(), new Limit(1, 4096))
                                        .<FileContent>map(target -> new FileContent.Link(target.text()));
                            case SUBMODULE ->
                                Either.<GitError, FileContent>right(
                                        new FileContent.Submodule(Sha.parse(entry.object())));
                            default -> blobContent(worktree, entry.object(), path, limit);
                        })
                        .getOrElse(() -> Either.left(new GitError.Unreadable(path, "not in " + sha.shortSha()))));
    }

    /**
     * The entry of the tree of {@code sha} at {@code path}, if it has one.
     */
    private static Either<GitError, Option<Entry>> entry(Path worktree, Sha sha, String path) {
        return GitProcess.read(
                worktree,
                GitCommand.reading("ls-tree", "-z", "--full-tree", sha.value(), "--", path)
                        .withLiteralPaths()
                        .withTimeout(TIMEOUT),
                raw -> Either.right(List.of(raw.split("\0"))
                        .flatMap(CliTreeFiles::entry)
                        .find(entry -> entry.path().equals(path))));
    }

    /**
     * An entry of {@code git ls-tree}: its mode, its type and its object, a tab and its path.
     */
    static Option<Entry> entry(String line) {
        int tab = line.indexOf('\t');
        if (tab < 0) {
            return Option.none();
        }
        String[] fields = line.substring(0, tab).split(" ");
        return fields.length == 3 ? Option.of(new Entry(fields[0], fields[2], line.substring(tab + 1))) : Option.none();
    }

    /**
     * The blob {@code object}, the file at {@code path}: binary when it has a NUL byte in its first 8000 bytes, as git
     * tells, else text cut at {@code limit}.
     */
    private static Either<GitError, FileContent> blobContent(Path worktree, String object, String path, Limit limit) {
        return blob(worktree, object, limit)
                .flatMap(read -> binary(read.text())
                        ? size(worktree, object).<FileContent>map(FileContent.Binary::new)
                        : Either.right(
                                new FileContent.Text(CliWorkingFiles.asDiff(path, read.text(), read.cut(), limit))));
    }

    private static Either<GitError, Limited> blob(Path worktree, String object, Limit limit) {
        return GitProcess.read(
                worktree, GitCommand.reading("cat-file", "blob", object).withTimeout(TIMEOUT), limit);
    }

    private static Either<GitError, Long> size(Path worktree, String object) {
        return GitProcess.read(
                worktree,
                GitCommand.reading("cat-file", "-s", object).withTimeout(TIMEOUT),
                out -> Try.of(() -> Long.parseLong(out.strip()))
                        .toEither()
                        .mapLeft(failure -> new GitError.UnexpectedOutput(out)));
    }

    /**
     * Whether {@code text} is of a binary file: one with a NUL in its first 8000 characters, as git tells by bytes.
     */
    static boolean binary(String text) {
        return text.substring(0, Math.min(text.length(), CliWorkingFiles.BINARY_PROBE))
                        .indexOf('\0')
                >= 0;
    }

    /**
     * An entry of a tree: the mode of the file, the object it is, and its path.
     */
    record Entry(String mode, String object, String path) {}
}
