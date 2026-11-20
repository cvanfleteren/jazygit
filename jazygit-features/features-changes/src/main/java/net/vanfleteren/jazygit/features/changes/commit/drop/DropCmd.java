package net.vanfleteren.jazygit.features.changes.commit.drop;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.operation.GitOperationCmd;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Removes a commit from the history of the checked out branch.
 */
public record DropCmd(Sha sha) implements GitOperationCmd {

    public static final Operation DROP =
            Operation.of("changes.log.title.drop", "changes.error.drop.failed").waitingForUnfinished();

    @Override
    public Operation operation() {
        return DROP;
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
        return git.commits().drop(sha);
    }
}
