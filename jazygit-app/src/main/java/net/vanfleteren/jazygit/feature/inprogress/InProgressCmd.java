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

    public static final Operation CONTINUE = Operation.of("log.title.continue", "error.continue.failed");

    public static final Operation ABORT = Operation.of("log.title.abort", "error.abort.failed");

    public static final Operation SKIP = Operation.of("log.title.skip", "error.skip.failed");

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
    public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
        return git.continueOrAbort(inProgress, action);
    }
}
