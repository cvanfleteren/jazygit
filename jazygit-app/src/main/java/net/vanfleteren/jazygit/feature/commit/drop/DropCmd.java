package net.vanfleteren.jazygit.feature.commit.drop;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.operation.GitOperationCmd;
import net.vanfleteren.jazygit.state.operation.Operation;

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
    public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
        return git.commits().drop(sha);
    }
}
