package net.vanfleteren.jazygit.git;

import static net.vanfleteren.jazygit.git.GitCommand.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import java.nio.file.Path;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.ResetMode;
import net.vanfleteren.jazygit.git.model.ResetTarget;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Moves the checked out branch through {@code git reset}.
 */
final class CliReset {

    private CliReset() {}

    /**
     * {@code git reset --quiet <mode> <revision>}: moves the checked out branch to {@code target}.
     *
     * @return the command lines that were run
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> reset(
            Path worktree, ResetTarget target, ResetMode mode) {
        return GitProcess.log(
                worktree,
                git("reset", "--quiet", mode.option(), target.revision().toArg()));
    }
}
