package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Undoes commits with a new commit through {@code git revert}. A revert that stops on conflicts is left so, for the
 * user to resolve them; one that fails otherwise is undone again, so the repository is left as it was. Git itself
 * refuses to start one while uncommitted changes would be overwritten, or while the repository is in the middle
 * of something else, which is then left alone.
 */
final class CliRevert {

    private static final GitCommand ABORT = git("revert", "--abort");

    private CliRevert() {}

    /**
     * {@code git revert --no-edit [-m 1] <sha>}: commits the inverse of {@code commit} on top of the checked out
     * branch, with the message git writes; a merge is reverted against its first parent.
     *
     * @return the command lines that were run
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> revert(Path workTree, Commit commit) {
        List<String> mainline = commit.isMerge() ? List.of("-m", "1") : List.of();
        return Unfinished.runOrUndo(
                workTree,
                git(List.of("revert", "--no-edit")
                        .appendAll(mainline)
                        .append(commit.sha().value())),
                left -> Option.when(left.picking(), ABORT));
    }
}
