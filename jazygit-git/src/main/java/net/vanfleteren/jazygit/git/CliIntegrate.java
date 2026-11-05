package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Files;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.InProgressAction;
import net.vanfleteren.jazygit.git.model.MergeMode;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.StackLayer;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * The {@link Integrate} of a repository, through {@code git merge}, {@code git cherry-pick}, {@code git revert} and
 * {@code git rebase}. One that stops on conflicts is left so, for the user to resolve them. Git itself refuses to
 * start one while the repository is in the middle of something else, which is then left alone, and a merge, a
 * cherry-pick or a revert while uncommitted changes would be overwritten; a rebase takes them along, stashed. How many
 * files one left with conflicts is counted through {@link #conflicts}.
 */
final class CliIntegrate implements Integrate {

    private static final GitCommand ABORT_PICK = git("cherry-pick", "--abort");
    private static final GitCommand ABORT_REVERT = git("revert", "--abort");

    private final Path worktree;

    CliIntegrate(Path worktree) {
        this.worktree = worktree;
    }

    /**
     * Merges {@code branch} into the checked out branch as {@code mode} says. Git refuses to start a merge while
     * changes are staged. A squash merge that stops on conflicts is not committed: git keeps no merge to continue,
     * so the user commits it once they are resolved.
     *
     * @return the command lines that were run
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> merge(BranchName branch, MergeMode mode) {
        return switch (mode) {
            case FAST_FORWARD -> withMessage(branch, "--ff");
            case MERGE_COMMIT -> withMessage(branch, "--no-ff");
            case SQUASH -> squash(branch);
            case SQUASH_AND_COMMIT -> GitFailure.sequence(() -> squash(branch), () -> commitStaged());
        };
    }

    /**
     * Merges the ref of {@code branch}, as a tag named like the branch would win over its bare name. Git would then
     * word the merge commit after the ref, so the message is given: the one {@code git fmt-merge-msg} writes for
     * the branch, as it does for {@code git merge <branch>}, so the user's {@code merge.*} config applies.
     */
    private Either<GitFailure, List<ExecutedCommand>> withMessage(BranchName branch, String fastForward) {
        return message(branch)
                .flatMap(message -> Unfinished.runOrStop(
                        worktree,
                        git(
                                "merge",
                                fastForward,
                                "--no-edit",
                                "-m",
                                message,
                                GitArgs.revision(new BranchRef.Local(branch)))));
    }

    private Either<GitFailure, String> message(BranchName branch) {
        return GitProcess.read(
                        worktree,
                        GitCommand.reading("rev-parse", "--verify", GitArgs.revision(new BranchRef.Local(branch))),
                        raw -> Either.right(raw.strip()))
                .mapLeft(error -> new GitFailure(error, List.empty()))
                .flatMap(sha -> TempFiles.using(
                        "jazygit-merge",
                        // What git merge hands to fmt-merge-msg for a local branch, as in FETCH_HEAD.
                        file -> Files.writeString(file, sha + "\t\tbranch '" + GitArgs.name(branch) + "' of .\n"),
                        failed -> new GitFailure(failed, List.empty()),
                        file -> GitProcess.read(
                                        worktree,
                                        GitCommand.reading("fmt-merge-msg", "--file", file.toString()),
                                        raw -> Either.right(raw.strip()))
                                .mapLeft(error -> new GitFailure(error, List.empty()))));
    }

    private Either<GitFailure, List<ExecutedCommand>> squash(BranchName branch) {
        return Unfinished.runOrStop(worktree, git("merge", "--squash", GitArgs.revision(new BranchRef.Local(branch))));
    }

    /**
     * How many files in the index have conflicts. Only asked, so not logged.
     */
    static Either<GitError, Integer> conflicts(Path worktree) {
        // "<mode> <object> <stage>\t<path>", a line for each stage of each conflicted path.
        return GitProcess.read(
                worktree,
                GitCommand.reading("ls-files", "--unmerged"),
                raw -> Either.right(List.of(raw.split("\n"))
                        .filter(line -> line.contains("\t"))
                        .map(line -> line.substring(line.indexOf('\t') + 1))
                        .distinct()
                        .size()));
    }

    /**
     * Commits what the squash staged, with git's message listing the squashed commits. A branch that had
     * nothing to squash staged nothing, and then nothing is committed.
     */
    private Either<GitFailure, List<ExecutedCommand>> commitStaged() {
        // diff --cached --quiet exits with 1, saying nothing, when something is staged.
        return GitProcess.ask(worktree, GitCommand.reading("diff", "--cached", "--quiet"))
                .flatMap(nothingStaged -> nothingStaged.isDefined()
                        ? GitFailure.nothing()
                        : GitProcess.log(worktree, git("commit", "--quiet", "--no-edit")));
    }

    /**
     * {@code git cherry-pick <sha>}: commits the changes of {@code sha} on top of the checked out branch. One that
     * stops because it changes nothing is undone again, so the repository is left as it was.
     *
     * @return the command lines that were run
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> cherryPick(Sha sha) {
        return Unfinished.runOrUndo(
                worktree, git("cherry-pick", sha.value()), left -> Option.when(left.picking(), ABORT_PICK));
    }

    /**
     * {@code git revert --no-edit [-m 1] <sha>}: commits the inverse of {@code commit} on top of the checked out
     * branch, with the message git writes; a merge is reverted against its first parent. One that fails other than
     * on conflicts is undone again, so the repository is left as it was.
     *
     * @return the command lines that were run
     */
    @Override
    public Either<GitFailure, List<ExecutedCommand>> revert(Commit commit) {
        List<String> mainline = commit.isMerge() ? List.of("-m", "1") : List.of();
        return Unfinished.runOrUndo(
                worktree,
                git(List.of("revert", "--no-edit")
                        .appendAll(mainline)
                        .append(commit.sha().value())),
                left -> Option.when(left.picking(), ABORT_REVERT));
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> continueOrAbort(InProgress inProgress, InProgressAction action) {
        return CliInProgress.continueOrAbort(worktree, inProgress, action);
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> rebase(BranchName onto) {
        return CliRebase.onto(worktree, onto);
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> rebaseStack(Revision base, List<StackLayer.Commits> layers) {
        return CliRebase.stack(worktree, base, layers);
    }
}
