package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;
import static net.vanfleteren.jazygit.git.GitCommand.reading;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitError.UnexpectedOutput;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.InProgressAction;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Finds out what the repository is in the middle of, from the files git keeps while it is, and continues, aborts
 * or skips it.
 */
final class CliInProgress {

    // --git-path finds them in a linked worktree too.
    private static final List<String> MARKERS =
            List.of("MERGE_HEAD", "rebase-merge", "rebase-apply", "CHERRY_PICK_HEAD", "REVERT_HEAD");
    private static final String BRANCH_REF = "refs/heads/";
    // Left by jazygit next to git's own files of a rebase that amends an older commit; git deletes it with them.
    private static final String AMENDING = "jazygit-amending";

    private CliInProgress() {}

    /**
     * What the repository in {@code worktree} is in the middle of, if anything. Only asked, so not logged.
     */
    static Either<ProcessError, Option<InProgress>> read(Path worktree) {
        return markers(worktree).map(CliInProgress::read);
    }

    /**
     * What the repository whose files are at {@code markers} is in the middle of, if anything.
     */
    static Option<InProgress> read(Markers markers) {
        return inProgress(markers);
    }

    /**
     * Where git keeps the files of the repository in {@code worktree} that tell what it is in the middle of. They
     * stay there, so this is asked once, and {@link #read(Markers)} then only looks at the files.
     */
    static Either<ProcessError, Markers> markers(Path worktree) {
        return GitProcess.read(
                worktree,
                reading(MARKERS.flatMap(marker -> List.of("--git-path", marker)).prepend("rev-parse")),
                raw -> paths(raw, worktree).map(Markers::new));
    }

    /**
     * {@code git <operation> --continue}, {@code --abort} or {@code --skip}. Continuing takes the message git
     * prepared, as the editor it would open is told to keep it. Going on can stop again, on the conflicts of a
     * next commit: that is where the user goes on from, so it is reported as {@link GitError.StoppedOnConflicts}.
     * Git refusing to go on, as conflicts remain where it was, is a failure.
     *
     * @return the command lines that were run
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> continueOrAbort(
            Path worktree, InProgress inProgress, InProgressAction action) {
        GitCommand command = git(subcommand(inProgress), option(action));
        return action == InProgressAction.ABORT
                ? GitFailure.widen(abort(worktree, inProgress, command))
                : GitProcess.log(worktree, command.withEnvironment(HashMap.of("GIT_EDITOR", "true")))
                        .mapLeft(failure -> Unfinished.stoppedAgain(worktree, inProgress, failure));
    }

    /**
     * Marks the rebase that stopped in {@code worktree} as one that amends an older commit: it started from a
     * commit of the staged changes, which it folds into that one, see {@link CliCommit#amend(Path, Sha)}. Aborting
     * it then takes that commit away again, so the changes are staged as they were. When the mark cannot be left,
     * aborting leaves that commit on the branch, as aborting it outside jazygit does.
     */
    static void markAmending(Path worktree) {
        markers(worktree)
                .forEach(markers ->
                        Try.run(() -> Files.createFile(markers.rebaseMerge().resolve(AMENDING))));
    }

    /**
     * Runs {@code abort}: {@code git <operation> --abort}, which puts the branch back where the operation started.
     * For a rebase that amends an older commit, that is the commit of the staged changes, which
     * {@code git reset --soft HEAD^} takes away again, keeping them staged. Only a rebase can be marked so.
     */
    private static Either<GitFailure<ProcessError>, List<ExecutedCommand>> abort(
            Path worktree, InProgress inProgress, GitCommand abort) {
        boolean amending = inProgress instanceof InProgress.Rebasing
                && markers(worktree)
                        .exists(markers -> Files.exists(markers.rebaseMerge().resolve(AMENDING)));
        return GitFailure.sequence(
                () -> GitProcess.log(worktree, abort),
                () -> amending
                        ? GitProcess.log(worktree, git("reset", "--quiet", "--soft", "HEAD^"))
                        : GitFailure.nothing());
    }

    private static String subcommand(InProgress inProgress) {
        return switch (inProgress) {
            case InProgress.Merging m -> "merge";
            case InProgress.Rebasing r -> "rebase";
            case InProgress.CherryPicking c -> "cherry-pick";
            case InProgress.Reverting r -> "revert";
        };
    }

    private static String option(InProgressAction action) {
        return switch (action) {
            case CONTINUE -> "--continue";
            case ABORT -> "--abort";
            case SKIP -> "--skip";
        };
    }

    private static Either<UnexpectedOutput, List<Path>> paths(String raw, Path worktree) {
        List<Path> paths =
                List.of(raw.split("\n")).filter(line -> !line.isEmpty()).map(worktree::resolve);
        return paths.size() == MARKERS.size() ? Either.right(paths) : Either.left(new UnexpectedOutput(raw));
    }

    /**
     * A rebase comes first: one that recreates a merge is merging too.
     */
    private static Option<InProgress> inProgress(Markers markers) {
        return rebasing(markers.rebaseMerge(), "msgnum", "end")
                .orElse(() -> rebasing(markers.rebaseApply(), "next", "last"))
                .orElse(() -> sha(markers.mergeHead()).map(InProgress.Merging::new))
                .orElse(() -> sha(markers.cherryPickHead()).map(InProgress.CherryPicking::new))
                .orElse(() -> sha(markers.revertHead()).map(InProgress.Reverting::new));
    }

    /**
     * The rebase git keeps in {@code dir}, which numbers its steps in the files {@code step} and {@code total}:
     * they differ between the merge and the apply backend. Git writes them once it gets to the first step, so
     * until then both are 0.
     */
    private static Option<InProgress> rebasing(Path dir, String step, String total) {
        return sha(dir.resolve("onto"))
                .map(onto -> new InProgress.Rebasing(
                        branch(dir.resolve("head-name")),
                        onto,
                        number(dir.resolve(step)).getOrElse(0),
                        number(dir.resolve(total)).getOrElse(0)));
    }

    /**
     * The branch named in {@code file} as {@code refs/heads/<branch>}; none when it says {@code detached HEAD}.
     */
    private static Option<Branch.Name> branch(Path file) {
        return firstLine(file)
                .filter(ref -> ref.startsWith(BRANCH_REF))
                .map(ref -> new Branch.Name(ref.substring(BRANCH_REF.length())));
    }

    private static Option<Integer> number(Path file) {
        return firstLine(file)
                .flatMap(line -> Try.of(() -> Integer.parseInt(line.strip())).toOption());
    }

    /**
     * The commit named on the first line of {@code file}; none when there is no such file.
     */
    private static Option<Sha> sha(Path file) {
        return firstLine(file).flatMap(Sha::parse);
    }

    private static Option<String> firstLine(Path file) {
        return Try.of(() -> Files.readAllLines(file))
                .toOption()
                .flatMap(lines -> Option.ofOptional(lines.stream().findFirst()));
    }

    /**
     * Where git keeps the files that tell what a repository is in the middle of, in the order of {@code MARKERS}.
     */
    record Markers(List<Path> paths) {

        Path mergeHead() {
            return paths.get(0);
        }

        Path rebaseMerge() {
            return paths.get(1);
        }

        Path rebaseApply() {
            return paths.get(2);
        }

        Path cherryPickHead() {
            return paths.get(3);
        }

        Path revertHead() {
            return paths.get(4);
        }
    }
}
