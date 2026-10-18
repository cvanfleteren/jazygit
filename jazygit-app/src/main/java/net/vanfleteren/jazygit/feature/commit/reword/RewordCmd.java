package net.vanfleteren.jazygit.feature.commit.reword;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.GitOperationCmd;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Replaces the message of a commit.
 */
public record RewordCmd(Sha sha, CommitMessage message) implements GitOperationCmd {

    public static final Operation REWORD =
            Operation.of("log.title.reword", "error.reword.failed").waitingForUnfinished();

    @Override
    public Operation operation() {
        return REWORD;
    }

    @Override
    public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
        return git.reword(sha, message);
    }
}
