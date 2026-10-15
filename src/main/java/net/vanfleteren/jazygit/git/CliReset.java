package net.vanfleteren.jazygit.git;

import net.vanfleteren.jazygit.process.ExecutedCommand;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.ResetMode;
import net.vanfleteren.jazygit.git.model.Sha;

import java.nio.file.Path;

import static net.vanfleteren.jazygit.git.GitCommand.git;

/**
 * Moves the checked out branch through {@code git reset}.
 */
final class CliReset {

    private CliReset() {
    }

    /**
     * {@code git reset --quiet <mode> <sha>}: moves the checked out branch to {@code sha}.
     *
     * @return the command lines that were run
     */
    static Either<GitFailure<ProcessError>, List<ExecutedCommand>> reset(Path workTree, Sha sha, ResetMode mode) {
        return GitProcess.log(workTree, git("reset", "--quiet", mode.option(), sha.value()));
    }
}
