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

    private CliInProgress() {}

    /**
     * What the repository in {@code workTree} is in the middle of, if anything. Only asked, so not logged.
     */
    static Either<ProcessError, Option<InProgress>> read(Path workTree) {
        return GitProcess.read(
                        workTree,
                        reading(MARKERS.flatMap(marker -> List.of("--git-path", marker))
                                .prepend("rev-parse")),
                        raw -> paths(raw, workTree))
                .map(CliInProgress::inProgress);
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
            Path workTree, InProgress inProgress, InProgressAction action) {
        GitCommand command = git(subcommand(inProgress), option(action));
        return action == InProgressAction.ABORT
                ? GitFailure.widen(GitProcess.log(workTree, command))
                : GitProcess.log(workTree, command.withEnvironment(HashMap.of("GIT_EDITOR", "true")))
                        .mapLeft(failure -> Unfinished.stoppedAgain(workTree, inProgress, failure));
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

    private static Either<UnexpectedOutput, List<Path>> paths(String raw, Path workTree) {
        List<Path> paths =
                List.of(raw.split("\n")).filter(line -> !line.isEmpty()).map(workTree::resolve);
        return paths.size() == MARKERS.size() ? Either.right(paths) : Either.left(new UnexpectedOutput(raw));
    }

    /**
     * A rebase comes first: one that recreates a merge is merging too.
     */
    private static Option<InProgress> inProgress(List<Path> markers) {
        return rebasing(markers.get(1), "msgnum", "end")
                .orElse(() -> rebasing(markers.get(2), "next", "last"))
                .orElse(() -> sha(markers.get(0)).map(InProgress.Merging::new))
                .orElse(() -> sha(markers.get(3)).map(InProgress.CherryPicking::new))
                .orElse(() -> sha(markers.get(4)).map(InProgress.Reverting::new));
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
}
