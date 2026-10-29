package net.vanfleteren.jazygit.git;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Vector;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * Reads what a commit changed through the git command line tool.
 */
final class CliShow {

    /**
     * Reads a merge against its first parent, as the files of a commit are always read.
     */
    static final String FIRST_PARENT = "--diff-merges=first-parent";

    private static final List<String> SHOW = List.of(
                    "show", "--format=", "--no-color", "--no-ext-diff", "--no-show-signature")
            .appendAll(CliDiff.PREFIXES);

    private CliShow() {}

    /**
     * The changed files with their change counts, a summary line, a blank line and the diff, in one
     * git call however many files the commit touches, read with {@code options}. Long output is cut off.
     */
    static Either<ProcessError, DiffText> changes(Path worktree, Sha sha, DiffOptions options) {
        return GitProcess.read(
                worktree,
                GitCommand.reading(SHOW.appendAll(List.of("--stat=1000", "--patch"))
                        .appendAll(flags(options))
                        .append(sha.value())),
                output -> Either.right(CliDiff.truncated(output)));
    }

    /**
     * The files the commit {@code sha} changed, a merge against its first parent, with renames found.
     */
    static Either<ProcessError, List<CommitFile>> files(Path worktree, Sha sha) {
        return GitProcess.read(
                worktree,
                GitCommand.reading(SHOW.appendAll(List.of("--name-status", "-z", "-M", FIRST_PARENT))
                        .append(sha.value())),
                CliShow::parseFiles);
    }

    /**
     * What the commit {@code sha} changed in {@code files}, a merge against its first parent, read with
     * {@code options}. Long output is cut off. Without files, nothing is read: git would show every file.
     */
    static Either<ProcessError, DiffText> fileChanges(
            Path worktree, Sha sha, List<CommitFile> files, DiffOptions options) {
        if (files.isEmpty()) {
            return Either.right(DiffText.of(""));
        }
        return GitProcess.read(
                worktree,
                GitCommand.reading(SHOW.appendAll(List.of("--patch", "-M", FIRST_PARENT))
                                .appendAll(CliDiff.flags(options))
                                .append(sha.value())
                                .append("--")
                                .appendAll(files.flatMap(CommitFile::paths)))
                        .withLiteralPaths(),
                output -> Either.right(CliDiff.truncated(output)));
    }

    /**
     * The flags of {@code git show} that read what a commit changed with {@code options}.
     */
    static List<String> flags(DiffOptions options) {
        return CliDiff.flags(options).appendAll(options.firstParent() ? List.of(FIRST_PARENT) : List.empty());
    }

    /**
     * The files of {@code --name-status -z} output: a status, then a path, or the path renamed from and the one
     * renamed to. A copy is taken as an added file, and a change of type as a modification.
     */
    static Either<UnexpectedOutput, List<CommitFile>> parseFiles(String raw) {
        Vector<String> fields = Vector.of(raw.split("\0")).filter(field -> !field.isEmpty());
        // Unfolded rather than recursive, as a commit can change more files than the stack is deep.
        return Either.sequenceRight(List.unfoldRight(
                        0,
                        at -> Option.when(
                                at < fields.size(),
                                () -> file(fields, at)
                                        .fold(
                                                () -> Tuple.of(
                                                        Either.<UnexpectedOutput, CommitFile>left(
                                                                new UnexpectedOutput(raw)),
                                                        fields.size()),
                                                file -> Tuple.of(Either.right(file._1), file._2)))))
                .map(List::ofAll);
    }

    /**
     * The file whose status is field {@code at} of {@code fields}, and the field after it; none when its paths are
     * missing.
     */
    private static Option<Tuple2<CommitFile, Integer>> file(Vector<String> fields, int at) {
        String status = fields.get(at);
        int count = status.startsWith("R") || status.startsWith("C") ? 2 : 1;
        Vector<String> paths = fields.drop(at + 1).take(count);
        return Option.when(
                paths.size() == count,
                () -> Tuple.of(
                        switch (status.charAt(0)) {
                            case 'R' -> new CommitFile(paths.last(), ChangeType.RENAMED, paths.headOption());
                            case 'C', 'A' -> new CommitFile(paths.last(), ChangeType.ADDED);
                            case 'D' -> new CommitFile(paths.last(), ChangeType.DELETED);
                            default -> new CommitFile(paths.last(), ChangeType.MODIFIED);
                        },
                        at + 1 + count));
    }
}
