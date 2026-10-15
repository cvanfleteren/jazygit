package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * A command that performs an {@link Operation} on the repository. Its outcome is reported as an
 * {@link OperationMsg}, which {@link OperationUpdate} handles the same way for every operation: the commands
 * are logged, a failure is shown, and the repository is read again. {@link GitOperationCmd} is one done
 * through git alone.
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
    default Option<BranchRef> busyBranch() {
        return Option.none();
    }

    /**
     * Whether the operation changes what GitHub knows, like a push, after which the pull requests and the checks
     * of the branches are read again.
     */
    default boolean changesGitHub() {
        return false;
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
