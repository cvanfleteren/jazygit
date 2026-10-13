package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * An {@link OperationCmd} done through git alone.
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
        return perform(env.git()).fold(this::failedWith, this::succeeded);
    }
}
