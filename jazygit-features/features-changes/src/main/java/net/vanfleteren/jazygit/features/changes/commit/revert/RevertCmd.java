package net.vanfleteren.jazygit.features.changes.commit.revert;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.kernel.state.operation.GitOperationCmd;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Commits the inverse of {@code commit} on top of the checked out branch.
 */
public record RevertCmd(Commit commit) implements GitOperationCmd {

    public static final Operation REVERT = Operation.of("changes.log.title.revert", "changes.error.revert.failed")
            .waitingForUnfinished();

    @Override
    public Operation operation() {
        return REVERT;
    }

    @Override
    public Option<String> subject() {
        return Option.of(commit.shortSha());
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
        return git.integrate().revert(commit);
    }
}
