package net.vanfleteren.jazygit.git.files;

import io.vavr.CheckedFunction0;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.FileContent;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.process.GitCommand;
import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.git.process.GitProcess;
import net.vanfleteren.jazygit.process.Limit;

/**
 * All files of a working tree, and what one of them holds on disk (ADR 108): the files pane lists them with its
 * changed files, and shows those without changes as they are.
 */
final class CliWorkingFiles {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    // How many bytes git looks at for a NUL to tell a binary file.
    private static final int BINARY_PROBE = 8000;
    // The tag ls-files -t gives a file with the skip-worktree bit.
    private static final char SKIP_WORKTREE = 'S';

    private CliWorkingFiles() {}

    /**
     * The paths of all files of the working tree at {@code worktree}, sorted, each once: git lists a conflicted file
     * once per stage, and with {@code -t} its {@code --deduplicate} does nothing. A file with the skip-worktree bit
     * that is not on disk is left out.
     */
    static Either<GitError, List<String>> allFiles(Path worktree) {
        return GitProcess.read(
                        worktree,
                        GitCommand.reading("ls-files", "-z", "-t", "--cached", "--others", "--exclude-standard")
                                .withTimeout(TIMEOUT),
                        raw -> Either.right(tagged(raw)))
                .map(listed -> listed.filter(file -> file.tag() != SKIP_WORKTREE
                                || Files.exists(worktree.resolve(file.path()), LinkOption.NOFOLLOW_LINKS))
                        .map(Tagged::path)
                        .distinct()
                        .sorted());
    }

    /**
     * The entries of {@code git ls-files -z -t}: a tag, a space and a path each.
     */
    static List<Tagged> tagged(String raw) {
        return List.of(raw.split("\0"))
                .filter(field -> field.length() > 2 && field.charAt(1) == ' ')
                .map(field -> new Tagged(field.charAt(0), field.substring(2)));
    }

    /**
     * What the file at {@code path} in the working tree at {@code worktree} holds: a link, a submodule, a binary file,
     * or text up to {@code limit}.
     */
    static Either<GitError, FileContent> content(Path worktree, String path, Limit limit) {
        Path file = worktree.resolve(path);
        if (Files.isSymbolicLink(file)) {
            return read(
                    path,
                    () -> new FileContent.Link(Files.readSymbolicLink(file).toString()));
        }
        if (Files.isDirectory(file)) {
            return Either.right(new FileContent.Submodule(submodule(worktree, path)));
        }
        return read(path, () -> {
            long size = Files.size(file);
            byte[] bytes;
            try (InputStream in = Files.newInputStream(file)) {
                bytes = in.readNBytes(limit.chars() + 1);
            }
            return binary(bytes) ? new FileContent.Binary(size) : new FileContent.Text(asDiff(path, bytes, limit));
        });
    }

    /**
     * The commit the index has for the submodule at {@code path}, if it can be told.
     */
    private static Option<Sha> submodule(Path worktree, String path) {
        return GitProcess.read(
                        worktree,
                        GitCommand.reading("rev-parse", "--verify", "--quiet", ":" + path)
                                .withTimeout(TIMEOUT),
                        out -> Either.right(Sha.parse(out.strip())))
                .toOption()
                .flatMap(sha -> sha);
    }

    /**
     * Whether {@code bytes} are of a binary file: one with a NUL byte in its first 8000, as git tells.
     */
    static boolean binary(byte[] bytes) {
        return List.range(0, Math.min(bytes.length, BINARY_PROBE)).exists(i -> bytes[i] == 0);
    }

    /**
     * The text of {@code bytes}, the start of the file at {@code path}, as a diff of the file against itself: one hunk
     * of all its lines as context, cut at {@code limit}. Read past the limit by one byte, so a file exactly as long is
     * not cut.
     */
    static DiffText asDiff(String path, byte[] bytes, Limit limit) {
        boolean longer = bytes.length > limit.chars();
        String text = new String(bytes, 0, Math.min(bytes.length, limit.chars()), StandardCharsets.UTF_8);
        boolean endsWithNewline = text.endsWith("\n");
        List<String> all = text.isEmpty()
                ? List.empty()
                : List.of((endsWithNewline ? text.substring(0, text.length() - 1) : text).split("\n", -1));
        List<String> lines = all.take(limit.lines());
        boolean truncated = longer || all.size() > limit.lines();
        String header = "diff --git a/" + path + " b/" + path + "\n--- a/" + path + "\n+++ b/" + path + "\n";
        String hunk = lines.isEmpty()
                ? ""
                : "@@ -1," + lines.size() + " +1," + lines.size() + " @@\n"
                        + lines.map(line -> " " + line + "\n").mkString()
                        + (endsWithNewline || truncated ? "" : "\\ No newline at end of file\n");
        return DiffText.of(header + hunk, truncated);
    }

    /**
     * What {@code reading} the file at {@code path} from disk gives, or why it could not.
     */
    private static <T extends FileContent> Either<GitError, FileContent> read(
            String path, CheckedFunction0<T> reading) {
        return Try.of(reading)
                .toEither()
                .<FileContent>map(content -> content)
                .mapLeft(failure -> new GitError.Unreadable(
                        path,
                        Option.of(failure.getMessage())
                                .getOrElse(failure.getClass().getSimpleName())));
    }

    /**
     * An entry of {@code git ls-files -t}: the tag of what git knows of the file, and its path.
     */
    record Tagged(char tag, String path) {}
}
