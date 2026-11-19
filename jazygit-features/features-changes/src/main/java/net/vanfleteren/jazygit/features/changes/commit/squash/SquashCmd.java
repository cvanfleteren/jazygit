package net.vanfleteren.jazygit.features.changes.commit.squash;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.model.Sha;
import net.vanfleteren.jazygit.core.git.model.SquashMode;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.state.operation.GitOperationCmd;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Folds a commit into its parent: a squash, or a fixup when {@code mode} drops either message.
 */
public record SquashCmd(Sha sha, SquashMode mode) implements GitOperationCmd {

    public static final Operation SQUASH = Operation.of("changes.log.title.squash", "changes.error.squash.failed")
            .waitingForUnfinished();

    public static final Operation FIXUP = Operation.of("changes.log.title.fixup", "changes.error.fixup.failed")
            .waitingForUnfinished();

    @Override
    public Operation operation() {
        return mode == SquashMode.SQUASH ? SQUASH : FIXUP;
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
        return git.commits().squash(sha, mode);
    }
}
