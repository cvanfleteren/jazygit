package net.vanfleteren.jazygit.core.git.files;

import static net.vanfleteren.jazygit.core.git.process.GitCommand.git;
import static net.vanfleteren.jazygit.core.git.process.GitCommand.reading;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import net.vanfleteren.jazygit.core.git.model.Conflict;
import net.vanfleteren.jazygit.core.git.model.ConflictFile;
import net.vanfleteren.jazygit.core.git.model.ConflictTools;
import net.vanfleteren.jazygit.core.git.model.DiscardPlan;
import net.vanfleteren.jazygit.core.git.model.MarkedFile;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitError.RefusedPath;
import net.vanfleteren.jazygit.core.git.process.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.git.process.GitProcess;
import net.vanfleteren.jazygit.core.process.CommandLine;
import net.vanfleteren.jazygit.core.process.Exec;
import net.vanfleteren.jazygit.core.process.ExecFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;

/**
 * The {@link Index} of a repository, the staging area, changed through the git command line tool.
 */
public final class CliIndex implements Index {

    // What a read of the index or the config may take.
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    // What mergiraf may take to merge one file.
    private static final Duration SOLVE_TIMEOUT = Duration.ofMinutes(2);
    private static final String MERGIRAF = "mergiraf";
    // What mergiraf solve exits with when it left conflicts in the file.
    private static final int CONFLICTS_LEFT = 2;

    private final Path worktree;

    public CliIndex(Path worktree) {
        this.worktree = worktree;
    }

    /**
     * {@code git add -- <paths>}: stages the changes of the paths.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> stage(List<String> paths) {
        return onPaths(worktree, List.of("add"), paths);
    }

    /**
     * {@code git rm --cached --force -- <paths>}: removes newly added paths from the index, leaving
     * them untracked in the working tree.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> unstageNew(List<String> paths) {
        return onPaths(worktree, List.of("rm", "--cached", "--force", "--quiet"), paths);
    }

    /**
     * {@code git reset HEAD -- <paths>}: resets the index entries of the paths to HEAD.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> unstage(List<String> paths) {
        return onPaths(worktree, List.of("reset", "--quiet", "HEAD"), paths);
    }

    /**
     * {@code git checkout -- <paths>}: resets the working tree of the paths to the index.
     */
    static Either<GitFailure, List<ExecutedCommand>> checkoutFromIndex(Path worktree, List<String> paths) {
        return onPaths(worktree, List.of("checkout", "--quiet"), paths);
    }

    /**
     * {@code git checkout HEAD -- <paths>}: resets the index and the working tree of the paths to HEAD.
     */
    static Either<GitFailure, List<ExecutedCommand>> checkoutFromHead(Path worktree, List<String> paths) {
        return onPaths(worktree, List.of("checkout", "--quiet", "HEAD"), paths);
    }

    /**
     * {@code git rm --force -- <paths>}: removes newly added paths from the index and the working tree.
     */
    static Either<GitFailure, List<ExecutedCommand>> removeAdded(Path worktree, List<String> paths) {
        return onPaths(worktree, List.of("rm", "--force", "--quiet"), paths);
    }

    /**
     * The commands of {@link Conflict#keeping}, each on {@code -- <path>}: {@code git checkout --ours|--theirs}, then
     * {@code git add}; or {@code git rm --quiet} when that side deleted the file.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> keep(String path, Conflict conflict, Conflict.Version version) {
        return GitFailure.sequence(
                conflict.keeping(version).map(arguments -> () -> onPaths(worktree, arguments, List.of(path))));
    }

    /**
     * {@code git checkout --merge -- <path>}: the conflict again, from what the index keeps of it once resolved.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> restoreConflict(String path) {
        return onPaths(worktree, List.of("checkout", "--merge"), List.of(path));
    }

    /**
     * Reads each file for blocks of conflict markers; a file that cannot be read, as one that is not there, has none.
     */
    @Override
    public Either<GitError, List<MarkedFile>> markers(List<String> paths) {
        Path root = worktree.toAbsolutePath().normalize();
        return Either.right(paths.flatMap(path -> inside(root, path)
                .toOption()
                .flatMap(file -> Try.of(() -> Files.readString(file)).toOption())
                .map(MarkedFile::blockStarts)
                .filter(lines -> !lines.isEmpty())
                .map(lines -> new MarkedFile(path, lines))));
    }

    /**
     * {@code git ls-files --unmerged -- <paths>}: those of them that have more than one stage in the index.
     */
    @Override
    public Either<GitError, List<String>> unmerged(List<String> paths) {
        return paths.isEmpty()
                ? Either.right(List.empty())
                : GitProcess.read(
                        worktree,
                        reading(List.of("ls-files", "--unmerged", "-z", "--").appendAll(paths))
                                .withLiteralPaths()
                                .withTimeout(TIMEOUT),
                        raw -> Either.right(List.of(raw.split("\0"))
                                .filter(entry -> entry.contains("\t"))
                                .map(entry -> entry.substring(entry.indexOf('\t') + 1))
                                .distinct()));
    }

    /**
     * {@code git config --get merge.tool} and {@code git config --get mergetool.keepBackup}, which fail when they are
     * not set, and {@code mergiraf --version}, which fails when it does not run.
     */
    @Override
    public Either<GitError, ConflictTools> tools() {
        return Either.right(new ConflictTools(
                configured("merge.tool"),
                configured("mergetool.keepBackup"),
                Exec.run(worktree, List.of(MERGIRAF, "--version"), HashMap.empty(), TIMEOUT)
                        .isRight()));
    }

    private boolean configured(String key) {
        return GitProcess.read(
                        worktree,
                        reading("config", "--get", key).withTimeout(TIMEOUT),
                        raw -> Either.right(!raw.isBlank()))
                .getOrElse(false);
    }

    /**
     * The file at {@code path} against the conflict {@code git merge-file -p} writes of its stages in the index,
     * which is what {@code git checkout --merge} writes, made in a directory of its own so the working tree is left as
     * it is. A file with no base, added on both sides, is merged from an empty one.
     */
    @Override
    public Either<GitError, ConflictFile> conflictFile(String path) {
        Path root = worktree.toAbsolutePath().normalize();
        return inside(root, path)
                .<GitError>mapLeft(refused -> refused)
                .flatMap(file -> Try.of(() -> Files.readString(file)).toEither().mapLeft(failure ->
                        (GitError) new GitError.Unreadable(path, String.valueOf(failure))))
                .flatMap(working -> written(path).map(conflict -> ConflictFile.of(working, conflict)));
    }

    /**
     * The conflict git writes of the stages of {@code path}: {@code git merge-file -p} of ours, the base and theirs,
     * which exits with the number of conflicts it wrote.
     */
    private Either<GitError, String> written(String path) {
        return Try.of(() -> Files.createTempDirectory("jazygit-conflict"))
                .toEither()
                .mapLeft(failure -> (GitError) new GitError.TempFileFailed(failure))
                .flatMap(directory -> {
                    Either<GitError, String> merged = writeStage(path, 2, directory.resolve("ours"))
                            .flatMap(ours -> writeStage(path, 1, directory.resolve("base"))
                                    .flatMap(base -> writeStage(path, 3, directory.resolve("theirs"))
                                            .flatMap(theirs -> mergeFile(ours, base, theirs))));
                    // Leftover temporary files are harmless.
                    List.of("ours", "base", "theirs")
                            .forEach(name -> Try.run(() -> Files.deleteIfExists(directory.resolve(name))));
                    Try.run(() -> Files.deleteIfExists(directory));
                    return merged;
                });
    }

    /**
     * Stage {@code number} of {@code path} written to {@code file}; empty when the index has no such stage, as a file
     * added on both sides has no base.
     */
    private Either<GitError, Path> writeStage(String path, int number, Path file) {
        String content = GitProcess.read(
                        worktree,
                        reading("show", ":" + number + ":" + path).withTimeout(TIMEOUT),
                        Either::<UnexpectedOutput, String>right)
                .getOrElse("");
        return Try.of(() -> Files.writeString(file, content)).toEither().mapLeft(failure ->
                (GitError) new GitError.TempFileFailed(failure));
    }

    private Either<GitError, String> mergeFile(Path ours, Path base, Path theirs) {
        List<String> commandLine =
                List.of("git", "merge-file", "-p", ours.toString(), base.toString(), theirs.toString());
        return Exec.run(worktree, commandLine, HashMap.empty(), TIMEOUT)
                .fold(
                        failure -> failure instanceof ExecFailure.NonZero(int conflicts, var stderr, String stdout)
                                        && conflicts > 0
                                        && conflicts < 128
                                ? Either.right(stdout)
                                : Either.left(new GitError.Unreadable(ours.toString(), failure.toString())),
                        Either::right);
    }

    /**
     * {@link ConflictTools#mergiraf}: {@code git checkout --conflict=diff3 -- <path>} when {@code rewrite}, then
     * {@code mergiraf solve --keep-backup=false <path>}. Mergiraf exits with 2 when it left conflicts, which is what
     * it does, not a failure.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> solve(String path, boolean rewrite) {
        List<List<String>> steps = ConflictTools.mergiraf(path, rewrite);
        return rewrite
                ? GitFailure.sequence(
                        () -> GitProcess.log(worktree, git(steps.head().tail()).withLiteralPaths()),
                        () -> mergiraf(steps.last()))
                : mergiraf(steps.last());
    }

    private Either<GitFailure, List<ExecutedCommand>> mergiraf(List<String> commandLine) {
        List<ExecutedCommand> ran = List.of(new ExecutedCommand(commandLine));
        return Exec.run(worktree, commandLine, HashMap.empty(), SOLVE_TIMEOUT)
                .fold(failure -> solved(commandLine, ran, failure), output -> Either.right(ran));
    }

    /**
     * What mergiraf failing with {@code failure} means: conflicts left, which is no failure, or one.
     */
    private static Either<GitFailure, List<ExecutedCommand>> solved(
            List<String> commandLine, List<ExecutedCommand> ran, ExecFailure failure) {
        String program = CommandLine.format(commandLine);
        return switch (failure) {
            case ExecFailure.NonZero(int exitCode, var stderr, var stdout) ->
                exitCode == CONFLICTS_LEFT
                        ? Either.right(ran)
                        : Either.left(new GitFailure(new GitError.ProgramFailed(program, exitCode), ran));
            case ExecFailure.NotStarted(var cause) ->
                Either.left(new GitFailure(new GitError.ProgramNotStarted(program, cause), ran));
            case ExecFailure.TimedOut _ -> Either.left(new GitFailure(new GitError.Interrupted(MERGIRAF), ran));
            case ExecFailure.Interrupted _ -> Either.left(new GitFailure(new GitError.Interrupted(MERGIRAF), ran));
            case ExecFailure.TooMuchOutput _ -> Either.left(new GitFailure(new GitError.Interrupted(MERGIRAF), ran));
        };
    }

    /**
     * Throws changes away as {@code plan} says.
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> discard(DiscardPlan plan) {
        return GitFailure.sequence(
                () -> removeAdded(worktree, plan.added()),
                () -> checkoutFromHead(worktree, plan.fromHead()),
                () -> checkoutFromIndex(worktree, plan.fromIndex()),
                () -> deleteUntracked(worktree, plan.untracked()));
    }

    /**
     * {@code git clean --force -d --quiet -- <paths>}: deletes untracked files, and untracked directories with
     * what is untracked in them. Git leaves ignored files alone, and a repository nested in the working tree.
     * Paths outside the working tree, and the working tree itself, are refused before git runs.
     */
    static Either<GitFailure, List<ExecutedCommand>> deleteUntracked(Path worktree, List<String> paths) {
        Path root = worktree.toAbsolutePath().normalize();
        return paths.map(path -> inside(root, path))
                .find(Either::isLeft)
                .<Either<GitFailure, List<ExecutedCommand>>>map(
                        refused -> Either.left(new GitFailure(refused.getLeft(), List.empty())))
                .getOrElse(() -> onPaths(worktree, List.of("clean", "--force", "-d", "--quiet"), paths));
    }

    /**
     * {@code path} resolved against the working tree {@code root}, if it is strictly inside it and not
     * inside {@code .git}.
     */
    static Either<RefusedPath, Path> inside(Path root, String path) {
        Path target = root.resolve(path).normalize();
        return target.equals(root) || !target.startsWith(root) || target.startsWith(root.resolve(".git"))
                ? Either.left(new RefusedPath(path))
                : Either.right(target);
    }

    /**
     * Runs {@code subcommand -- paths}; nothing when there are no paths.
     */
    private static Either<GitFailure, List<ExecutedCommand>> onPaths(
            Path worktree, List<String> subcommand, List<String> paths) {
        return paths.isEmpty()
                ? GitFailure.nothing()
                : GitProcess.log(
                        worktree, git(subcommand.append("--").appendAll(paths)).withLiteralPaths());
    }
}
