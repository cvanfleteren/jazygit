package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * An {@link OperationCmd} done through git alone. One that stops on conflicts, left for the user to resolve, is
 * reported as {@link OperationMsg.Stopped} rather than as a failure.
 */
public interface GitOperationCmd extends OperationCmd {

    /**
     * Performs the operation.
     *
     * @return the commands that were run, or why it failed
     */
    Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git);

    @Override
    default Msg run(Env env) {
        return perform(env.git())
                .fold(
                        failure -> failure.error() instanceof GitError.StoppedOnConflicts(var inProgress, int conflicts)
                                ? new OperationMsg.Stopped(
                                        this, failure.commands(), new Notice.StoppedOnConflicts(inProgress, conflicts))
                                : failedWith(failure),
                        this::succeeded);
    }
}
