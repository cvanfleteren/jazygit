package net.vanfleteren.jazygit.feature.commit.squash;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.SquashMode;
import net.vanfleteren.jazygit.git.process.GitFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.operation.GitOperationCmd;
import net.vanfleteren.jazygit.state.operation.Operation;

/**
 * Folds a commit into its parent: a squash, or a fixup when {@code mode} drops either message.
 */
public record SquashCmd(Sha sha, SquashMode mode) implements GitOperationCmd {

    public static final Operation SQUASH =
            Operation.of("log.title.squash", "error.squash.failed").waitingForUnfinished();

    public static final Operation FIXUP =
            Operation.of("log.title.fixup", "error.fixup.failed").waitingForUnfinished();

    @Override
    public Operation operation() {
        return mode == SquashMode.SQUASH ? SQUASH : FIXUP;
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
        return git.commits().squash(sha, mode);
    }
}
