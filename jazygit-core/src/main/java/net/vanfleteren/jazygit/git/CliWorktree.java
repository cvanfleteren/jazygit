package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;
import static net.vanfleteren.jazygit.git.Refs.HEADS;

import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.*;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.Head;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Lists, adds and removes worktrees through the {@code git worktree} command line tool.
 */
final class CliWorktree {

    // What separates the worktrees in the -z porcelain output: the NUL ending the last line of one, and an empty
    // line.
    private static final String RECORD_SEPARATOR = "\0\0";
    // What git says when the worktree to remove has changes that removing it would lose. It is matched in
    // English, so git is run with the C locale.
    private static final String HAS_CHANGES = "contains modified or untracked files";

    private CliWorktree() {}

    /**
     * Runs {@code git worktree list --porcelain -z}: the worktrees, {@code worktree}, the one that is current, first,
     * then the others as git lists them, the main one first.
     */
    static Either<ProcessError, List<Worktree>> list(Path worktree) {
        return GitProcess.read(
                worktree, GitCommand.reading("worktree", "list", "--porcelain", "-z"), raw -> parse(raw, worktree));
    }

    /**
     * Runs {@code git status --porcelain -z --untracked-files=normal} in {@code worktree}: it has changes when git
     * lists any file.
     */
    static Either<ProcessError, Boolean> hasChanges(Path worktree) {
        return GitProcess.read(
                worktree,
                GitCommand.reading("status", "--porcelain", "-z", "--untracked-files=normal"),
                raw -> Either.right(!raw.isEmpty()));
    }

    /**
     * The worktrees in the output of {@code git worktree list --porcelain -z}: per worktree, one NUL-terminated
     * line per attribute, the first naming it, and an empty line after it. A bare repository is listed too, first,
     * and left out, as it has no working tree.
     */
    static Either<UnexpectedOutput, List<Worktree>> parse(String raw, Path current) {
        List<List<String>> records = List.of(raw.split(RECORD_SEPARATOR))
                .map(record -> List.of(record.split("\0")).filter(line -> !line.isEmpty()))
                .filter(List::nonEmpty);
        return Either.sequenceRight(records.zipWithIndex().map(record -> worktree(record._1, record._2 == 0, current)))
                .map(worktrees -> worktrees.flatMap(worktree -> worktree).toList())
                // The one shown first, as the checked out branch is; the others in the order git has them.
                .map(worktrees -> worktrees
                        .filter(Worktree::current)
                        .appendAll(worktrees.filter(worktree -> !worktree.current())));
    }

    /**
     * The worktree {@code lines} describe; none for a bare repository.
     */
    private static Either<UnexpectedOutput, Option<Worktree>> worktree(
            List<String> lines, boolean first, Path current) {
        Either<UnexpectedOutput, Path> path = GitOutput.after(lines.head(), "worktree ")
                .flatMap(text -> Try.of(() -> Path.of(text)).toOption())
                .toEither(() -> new UnexpectedOutput(lines.head()));
        Either<UnexpectedOutput, Option<Sha>> sha = value(lines, "HEAD")
                .map(text -> GitOutput.sha(text).map(Option::of))
                .getOrElse(() -> Either.right(Option.none()));
        Option<Branch.Name> branch = value(lines, "branch")
                .flatMap(ref -> GitOutput.after(ref, HEADS))
                .map(Branch.Name::new);
        return lines.contains("bare")
                ? Either.right(Option.none())
                : path.flatMap(root -> sha.map(at -> Option.of(new Worktree(
                        root,
                        head(at, branch),
                        first,
                        root.equals(current),
                        reason(lines, "locked"),
                        reason(lines, "prunable").isDefined()))));
    }

    /**
     * What the HEAD of a worktree points to: {@code branch}, or else the commit {@code sha}; none when it has
     * neither, as when git cannot resolve it: while {@code git worktree add} sets it up, or when its HEAD file is
     * broken.
     */
    private static Option<Head> head(Option<Sha> sha, Option<Branch.Name> branch) {
        // A repository without commits, and a HEAD git cannot resolve, have a HEAD of zeroes.
        Option<Sha> commit = sha.filter(at -> !at.value().matches("0+"));
        return branch.<Head>map(name -> new Head.OnBranch(name, commit)).orElse(() -> commit.map(Head.Detached::new));
    }

    /**
     * The value of the attribute {@code name}, if {@code lines} have it.
     */
    private static Option<String> value(Seq<String> lines, String name) {
        return lines.iterator()
                .flatMap(line -> GitOutput.after(line, name + " "))
                .headOption();
    }

    /**
     * The reason {@code lines} give for the attribute {@code name}, if they have it: empty when it has none.
     */
    private static Option<String> reason(Seq<String> lines, String name) {
        return lines.iterator()
                .flatMap(line -> line.equals(name) ? Option.of("") : GitOutput.after(line, name + " "))
                .headOption();
    }

    /**
     * Runs {@code git worktree add --quiet <location> <branch>} in {@code worktree}.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> add(
            Path worktree, Path location, Branch.Name branch) {
        return GitProcess.log(
                worktree,
                git(
                        "worktree",
                        "add",
                        "--quiet",
                        worktree.resolve(location).normalize().toString(),
                        branch.value()));
    }

    /**
     * Runs {@code git worktree add --quiet --detach <location> <startPoint>} in {@code worktree}: a new worktree
     * with {@code startPoint} checked out, on no branch.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> addDetached(
            Path worktree, Path location, String startPoint) {
        return GitProcess.log(
                worktree,
                git(
                        "worktree",
                        "add",
                        "--quiet",
                        "--detach",
                        worktree.resolve(location).normalize().toString(),
                        startPoint));
    }

    /**
     * Runs {@code git worktree add --quiet [--track] -b <name> <location> <startPoint>} in {@code worktree}: a new
     * branch, checked out in a new worktree. With {@code track}, the new branch follows {@code startPoint}, a
     * remote-tracking branch. A {@code parent} is recorded as the branch it came from, as a new branch records it.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> addNewBranch(
            Path worktree,
            Path location,
            Branch.Name name,
            String startPoint,
            Option<Branch.Name> parent,
            boolean track) {
        return GitFailure.sequence(
                () -> GitProcess.log(
                        worktree,
                        git(List.of("worktree", "add", "--quiet")
                                .appendAll(Option.when(track, "--track"))
                                .appendAll(List.of(
                                        "-b",
                                        name.value(),
                                        worktree.resolve(location).normalize().toString(),
                                        startPoint)))),
                () -> parent.map(p -> CliCheckout.recordParent(worktree, name, p))
                        .getOrElse(GitFailure::nothing));
    }

    /**
     * Runs {@code git worktree remove [--force] <location>} in {@code worktree}.
     *
     * @return the command lines that were run, or why git refused, {@link WorktreeHasChanges} when forcing it
     * would let the removal go through
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> remove(Path worktree, Path location, boolean force) {
        return GitProcess.log(
                        worktree,
                        git(List.of("worktree", "remove")
                                .appendAll(Option.when(force, "--force"))
                                .append(worktree.resolve(location).normalize().toString())))
                .mapLeft(failure -> failure.map(CliWorktree::classify));
    }

    /**
     * Runs {@code git worktree prune} in {@code worktree}: forgets the worktrees whose directory is gone.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> prune(Path worktree) {
        return GitProcess.log(worktree, git("worktree", "prune"));
    }

    /**
     * Runs {@code git worktree lock <location>}, or {@code unlock} when not {@code lock}, in {@code worktree}.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> lock(Path worktree, Path location, boolean lock) {
        return GitProcess.log(
                worktree,
                git(
                        "worktree",
                        lock ? "lock" : "unlock",
                        worktree.resolve(location).normalize().toString()));
    }

    /**
     * Runs {@code git worktree lock --reason <reason> <location>} in {@code worktree}, or without a reason when
     * {@code reason} is blank.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> lock(Path worktree, Path location, String reason) {
        String where = worktree.resolve(location).normalize().toString();
        return GitProcess.log(
                worktree,
                reason.isBlank() ? git("worktree", "lock", where) : git("worktree", "lock", "--reason", reason, where));
    }

    static GitError classify(ProcessError error) {
        return error instanceof CommandFailed failed && failed.says(HAS_CHANGES)
                ? new WorktreeHasChanges(failed.explanation().getOrElse(""))
                : error;
    }
}
