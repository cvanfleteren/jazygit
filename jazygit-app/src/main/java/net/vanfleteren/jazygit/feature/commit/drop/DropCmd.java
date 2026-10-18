package net.vanfleteren.jazygit.feature.commit.drop;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.GitOperationCmd;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Removes a commit from the history of the checked out branch.
 */
public record DropCmd(Sha sha) implements GitOperationCmd {

    public static final Operation DROP =
            Operation.of("log.title.drop", "error.drop.failed").waitingForUnfinished();

    @Override
    public Operation operation() {
        return DROP;
    }

    @Override
    public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
        return git.dropCommit(sha);
    }
}
