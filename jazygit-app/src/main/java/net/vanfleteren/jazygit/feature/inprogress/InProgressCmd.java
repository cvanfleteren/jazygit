package net.vanfleteren.jazygit.feature.inprogress;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.InProgressAction;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.GitOperationCmd;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Does {@code action} to {@code inProgress}, the operation the repository stopped in the middle of.
 */
public record InProgressCmd(InProgress inProgress, InProgressAction action) implements GitOperationCmd {

    @Override
    public Operation operation() {
        return switch (action) {
            case CONTINUE -> Operation.CONTINUE;
            case ABORT -> Operation.ABORT;
            case SKIP -> Operation.SKIP;
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
    public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
        return git.continueOrAbort(inProgress, action);
    }
}
