package net.vanfleteren.jazygit.git;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.process.ExecutedCommand;

import java.nio.file.Path;

import static net.vanfleteren.jazygit.git.GitCommand.git;

/**
 * Pulls the checked out branch through {@code git pull}, so the remote is reached with the user's own
 * configuration and credentials.
 */
final class CliPull {

    private CliPull() {
    }

    /**
     * Runs {@code git pull --no-edit}: pulls the checked out branch from its upstream, merging or rebasing as
     * git's {@code pull.rebase} and {@code pull.ff} say. A merge commit gets git's own message instead of an
     * editor, and with {@code pull.rebase=interactive} the todo list is taken as it is. A pull that stops on
     * conflicts is undone again, so the repository is left as it was; one that git refuses to start, as the
     * repository is in the middle of something else, leaves that alone.
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> pull(Path workTree, Map<String, String> credentials) {
        return CliRemote.toRemote(workTree, git("pull", "--no-edit").withEnvironment(HashMap.of("GIT_SEQUENCE_EDITOR", ":")),
                        credentials)
                .flatMap(pull -> Unfinished.runOrUndo(workTree, pull, left -> left.rebasing()
                        ? Option.of(CliRebase.ABORT)
                        : Option.when(left.conflicted(), CliMerge.ABORT)));
    }
}
