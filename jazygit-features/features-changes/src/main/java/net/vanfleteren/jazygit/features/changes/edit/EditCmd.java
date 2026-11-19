package net.vanfleteren.jazygit.features.changes.edit;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Opens {@code files}, relative to the working tree, in git's editor, with the terminal handed over to it. Once it
 * is closed, the repository is read again, like after any operation.
 */
public record EditCmd(List<String> files) implements OperationCmd {

    public static final Operation EDIT = Operation.of("changes.log.title.edit", "changes.error.edit.failed");

    @Override
    public Operation operation() {
        return EDIT;
    }

    @Override
    public Option<String> subject() {
        return Option.of(files.mkString(", "));
    }

    @Override
    public Msg run(Env env) {
        return env.terminal()
                .handOver(() -> env.git().editor().edit(files))
                .mapLeft(Failure::of)
                .fold(this::failed, this::succeeded);
    }
}
