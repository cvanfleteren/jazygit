package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.git.ExecutedCommand;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;

/**
 * A command that performs an {@link Operation} on the repository. Its outcome is reported as an
 * {@link OperationMsg}, which {@link OperationUpdate} handles the same way for every operation: the commands
 * are logged, a failure is shown, and the repository is read again.
 */
public interface OperationCmd extends Cmd {

    Operation operation();

    /**
     * What the operation is done to, e.g. the branch that is pushed, if its error message names it.
     */
    default Option<String> subject() {
        return Option.none();
    }

    /**
     * The branch that is marked busy while this runs, if any; it is released when this is done.
     */
    default Option<String> busyBranch() {
        return Option.none();
    }

    /**
     * Performs the operation.
     *
     * @return the commands that were run, or why it failed
     */
    Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git);

    @Override
    default Msg run(GitInfoProvider git) {
        return perform(git).fold(this::failedWith, this::succeeded);
    }

    /**
     * The {@link Msg} that reports this operation running {@code commands} successfully.
     */
    default Msg succeeded(List<ExecutedCommand> commands) {
        return new OperationMsg.Succeeded(this, commands);
    }

    @Override
    default Msg failed(Failure failure) {
        return new OperationMsg.Failed(this, failure);
    }
}
