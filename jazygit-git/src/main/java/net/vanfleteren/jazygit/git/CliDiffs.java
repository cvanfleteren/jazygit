package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.reading;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Vector;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.vanfleteren.jazygit.git.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.git.model.ChangeType;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DiffOptions;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.Limit;
import net.vanfleteren.jazygit.process.Limited;

/**
 * The {@link Diffs} of a repository, through the {@code git diff} and {@code git show} command line tools. The
 * stashes take their flags from {@link #PREFIXES} and {@link #flags}, and the files of a pull request are read
 * through {@link #rangeFiles(Path, Sha, Sha)} once its commits are fetched.
 */
final class CliDiffs implements Diffs {

    // The most of a diff that is read and shown (ADR 79): its first lines up to either.
    static final Limit LIMIT = new Limit(50_000, 4 * 1024 * 1024);
    /**
     * The flags that keep git's own {@code a/} and {@code b/} before the paths of a diff that is parsed
     * ({@code UnifiedDiff}), whatever the user's {@code diff.noprefix}, {@code diff.mnemonicPrefix},
     * {@code diff.srcPrefix} or {@code diff.dstPrefix} say.
     */
    static final List<String> PREFIXES = List.of("--src-prefix=a/", "--dst-prefix=b/");

    /**
     * The same for {@code git stash show}, which on git 2.55.0 garbles the paths when given {@link #PREFIXES}
     * (issue 287). {@code --default-prefix} (git 2.41 or newer) is right on every release, also with the user's
     * prefix settings.
     */
    static final List<String> STASH_SHOW_PREFIXES = List.of("--default-prefix");

    /**
     * Reads a merge against its first parent, as the files of a commit are always read.
     */
    private static final String FIRST_PARENT = "--diff-merges=first-parent";

    private static final List<String> STAGED =
            List.of("diff", "--cached", "--no-color", "--no-ext-diff").appendAll(PREFIXES);
    private static final List<String> DIFF =
            List.of("diff", "--no-color", "--no-ext-diff").appendAll(PREFIXES);
    private static final List<String> SHOW = List.of(
                    "show", "--format=", "--no-color", "--no-ext-diff", "--no-show-signature")
            .appendAll(PREFIXES);

    private final Path worktree;

    CliDiffs(Path worktree) {
        this.worktree = worktree;
    }

    /**
     * The changed files with their change counts, a summary line, a blank line and the diff, in one
     * git call however many files the commit touches, read with {@code options}. Long output is cut off.
     */
    @Override
    public Either<GitError, DiffText> commitChanges(Sha sha, DiffOptions options) {
        return diff(
                worktree,
                reading(SHOW.appendAll(List.of("--stat=1000", "--patch"))
                        .appendAll(showFlags(options))
                        .append(sha.value())));
    }

    /**
     * The files the commit {@code sha} changed, a merge against its first parent, with renames found.
     */
    @Override
    public Either<GitError, List<CommitFile>> commitFiles(Sha sha) {
        return GitProcess.read(
                worktree,
                reading(SHOW.appendAll(List.of("--name-status", "-z", "-M", FIRST_PARENT))
                        .append(sha.value())),
                CliDiffs::parseFiles);
    }

    /**
     * What the commit {@code sha} changed in {@code files}, a merge against its first parent, read with
     * {@code options}. Long output is cut off. Without files, nothing is read: git would show every file.
     */
    @Override
    public Either<GitError, DiffText> commitFileChanges(Sha sha, List<CommitFile> files, DiffOptions options) {
        if (files.isEmpty()) {
            return Either.right(DiffText.of(""));
        }
        return diff(
                worktree,
                reading(SHOW.appendAll(List.of("--patch", "-M", FIRST_PARENT))
                                .appendAll(flags(options))
                                .append(sha.value())
                                .append("--")
                                .appendAll(files.flatMap(CommitFile::paths)))
                        .withLiteralPaths());
    }

    /**
     * What the pull request of {@code commits} changes in {@code files}: its head against the merge base of it and
     * its base commit, {@code <base>...<head>}, as GitHub shows it (ADR 55).
     */
    @Override
    public Either<GitError, DiffText> pullRequestFileChanges(
            PullRequestCommits commits, List<CommitFile> files, DiffOptions options) {
        return rangeFileChanges(commits.base(), commits.head(), files, options);
    }

    @Override
    public Either<GitError, List<CommitFile>> rangeFiles(Sha base, Sha tip) {
        return rangeFiles(worktree, base, tip);
    }

    /**
     * The files {@code tip} changes against the merge base of it and {@code base}, with renames found: a branch
     * against its base (ADR 61), and a pull request once its commits are fetched.
     */
    static Either<GitError, List<CommitFile>> rangeFiles(Path worktree, Sha base, Sha tip) {
        return GitProcess.read(
                worktree,
                reading(List.of("diff", "--name-status", "-z", "-M").append(range(base, tip))),
                CliDiffs::parseFiles);
    }

    /**
     * What {@code tip} changes in {@code files} against the merge base of it and {@code base}, read with
     * {@code options}. Long output is cut off; without files, nothing is read: git would show every file.
     */
    @Override
    public Either<GitError, DiffText> rangeFileChanges(Sha base, Sha tip, List<CommitFile> files, DiffOptions options) {
        if (files.isEmpty()) {
            return Either.right(DiffText.of(""));
        }
        return diff(
                worktree,
                reading(DIFF.append("-M")
                                .appendAll(flags(options))
                                .append(range(base, tip))
                                .append("--")
                                .appendAll(files.flatMap(CommitFile::paths)))
                        .withLiteralPaths());
    }

    private static String range(Sha base, Sha tip) {
        return GitArgs.symmetric(new Revision.Commit(base), new Revision.Commit(tip));
    }

    /**
     * The staged changes of the files (index against HEAD), and their unstaged changes (working tree
     * against the index), read with {@code options}. Untracked files are shown as entirely added. Long diffs
     * are cut off.
     */
    @Override
    public Either<GitError, net.vanfleteren.jazygit.git.model.Diffs> uncommitted(
            List<FileEntry> files, DiffOptions options) {
        // A rename only shows as one when both of its paths are diffed.
        List<String> staged = files.filter(f -> f.staged() && f.type() != ChangeType.UNTRACKED)
                .flatMap(f -> f.paths(true));
        List<String> unstaged = files.filter(f -> f.unstaged() && f.type() != ChangeType.UNTRACKED)
                .flatMap(f -> f.paths(false));
        List<String> untracked = FileEntry.paths(files, f -> f.type() == ChangeType.UNTRACKED);
        List<String> unstagedDiff = DIFF.appendAll(flags(options)).append("--");
        return tracked(worktree, STAGED.appendAll(flags(options)).append("--"), staged, HashMap.empty())
                .flatMap(stagedDiff -> unstagedAndUntracked(worktree, unstagedDiff, unstaged, untracked)
                        .map(unstagedChanges ->
                                new net.vanfleteren.jazygit.git.model.Diffs(stagedDiff, unstagedChanges)));
    }

    /**
     * The flags of {@code git diff} and {@code git show} that read diffs with {@code options}.
     */
    static List<String> flags(DiffOptions options) {
        return List.of(options.context().flag())
                .appendAll(options.ignoreWhitespace() ? List.of("--ignore-all-space") : List.empty());
    }

    /**
     * The flags of {@code git show} that read what a commit changed with {@code options}.
     */
    private static List<String> showFlags(DiffOptions options) {
        return flags(options).appendAll(options.firstParent() ? List.of(FIRST_PARENT) : List.empty());
    }

    /**
     * Untracked files are marked intent-to-add in a temporary copy of the index, so one {@code git diff}
     * shows them as entirely added next to the unstaged changes. The real index is left untouched.
     */
    private static Either<GitError, DiffText> unstagedAndUntracked(
            Path worktree, List<String> subcommand, List<String> unstaged, List<String> untracked) {
        return untracked.isEmpty()
                ? tracked(worktree, subcommand, unstaged, HashMap.empty())
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

    private static Either<GitError, DiffText> withUntracked(
            Path worktree,
            List<String> subcommand,
            List<String> unstaged,
            List<String> untracked,
            Map<String, String> env) {
        return run(worktree, List.of("add", "--intent-to-add", "--").appendAll(untracked), env)
                .flatMap(added -> tracked(worktree, subcommand, unstaged.appendAll(untracked), env));
    }

    private static Either<GitError, DiffText> tracked(
            Path worktree, List<String> subcommand, List<String> paths, Map<String, String> env) {
        return paths.isEmpty()
                ? Either.right(DiffText.of(""))
                : diff(
                        worktree,
                        reading(subcommand.appendAll(paths))
                                .withEnvironment(env)
                                .withLiteralPaths());
    }

    /**
     * The diff {@code command} prints, read only up to {@link #LIMIT}.
     */
    static Either<GitError, DiffText> diff(Path worktree, GitCommand command) {
        return GitProcess.read(worktree, command, LIMIT).map(CliDiffs::diff);
    }

    /**
     * {@code read} as a diff, cut off when it was.
     */
    static DiffText diff(Limited read) {
        return DiffText.of(read.text(), read.cut());
    }

    private static Either<GitError, String> run(Path worktree, List<String> args, Map<String, String> env) {
        return GitProcess.read(worktree, reading(args).withEnvironment(env).withLiteralPaths(), Either::right);
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
