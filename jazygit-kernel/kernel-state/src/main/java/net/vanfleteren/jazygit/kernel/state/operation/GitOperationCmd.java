package net.vanfleteren.jazygit.kernel.state.operation;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;

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
    Either<GitFailure, List<ExecutedCommand>> perform(Git git);

    @Override
    default Msg run(Env env) {
        return perform(env.git())
                .fold(
                        failure -> failure.error() instanceof GitError.StoppedOnConflicts(var inProgress, int conflicts)
                                ? new OperationMsg.Stopped(
                                        this, failure.commands(), new Notice.StoppedOnConflicts(inProgress, conflicts))
                                : failed(Failure.of(failure)),
                        this::succeeded);
    }
}
