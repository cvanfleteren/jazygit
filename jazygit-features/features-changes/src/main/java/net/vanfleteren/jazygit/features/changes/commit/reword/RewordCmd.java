package net.vanfleteren.jazygit.features.changes.commit.reword;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.model.CommitMessage;
import net.vanfleteren.jazygit.core.git.model.Sha;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.state.operation.GitOperationCmd;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Replaces the message of a commit.
 */
public record RewordCmd(Sha sha, CommitMessage message) implements GitOperationCmd {

    public static final Operation REWORD = Operation.of("changes.log.title.reword", "changes.error.reword.failed")
            .waitingForUnfinished();

    @Override
    public Operation operation() {
        return REWORD;
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
        return git.commits().reword(sha, message);
    }
}
