package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Copies commits onto the checked out branch through {@code git cherry-pick}. A cherry-pick that stops on
 * conflicts is left so, for the user to resolve them; one that stops because it changes nothing is undone again,
 * so the repository is left as it was. Git itself
 * refuses to start one while uncommitted changes would be overwritten, or while the repository is in the middle
 * of something else, which is then left alone.
 */
final class CliCherryPick {

    private static final GitCommand ABORT = git("cherry-pick", "--abort");

    private CliCherryPick() {}

    /**
     * {@code git cherry-pick <sha>}: commits the changes of {@code sha} on top of the checked out branch.
     *
     * @return the command lines that were run
     */
    static Either<GitFailure<GitError>, List<ExecutedCommand>> cherryPick(Path worktree, Sha sha) {
        return Unfinished.runOrUndo(
                worktree, git("cherry-pick", sha.value()), left -> Option.when(left.picking(), ABORT));
    }
}
