package net.vanfleteren.jazygit.features.changes.inprogress;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.model.InProgress;
import net.vanfleteren.jazygit.core.git.model.InProgressAction;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.state.operation.GitOperationCmd;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Does {@code action} to {@code inProgress}, the operation the repository stopped in the middle of.
 */
public record InProgressCmd(InProgress inProgress, InProgressAction action) implements GitOperationCmd {

    public static final Operation CONTINUE =
            Operation.of("changes.log.title.continue", "changes.error.continue.failed");

    public static final Operation ABORT = Operation.of("changes.log.title.abort", "changes.error.abort.failed");

    public static final Operation SKIP = Operation.of("changes.log.title.skip", "changes.error.skip.failed");

    @Override
    public Operation operation() {
        return switch (action) {
            case CONTINUE -> InProgressCmd.CONTINUE;
            case ABORT -> InProgressCmd.ABORT;
            case SKIP -> InProgressCmd.SKIP;
        };
    }

    /**
     * The git command of the operation, e.g. {@code rebase}.
     */
    @Override
    public Option<String> subject() {
        return Option.of(
                switch (inProgress) {
                    case InProgress.Merging m -> "merge";
                    case InProgress.Rebasing r -> "rebase";
                    case InProgress.CherryPicking c -> "cherry-pick";
                    case InProgress.Reverting r -> "revert";
                });
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
        return git.integrate().continueOrAbort(inProgress, action);
    }
}
