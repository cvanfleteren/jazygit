package net.vanfleteren.jazygit.feature.edit;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.OperationCmd;

/**
 * Opens {@code files}, relative to the working tree, in git's editor, with the terminal handed over to it. Once it
 * is closed, the repository is read again, like after any operation.
 */
public record EditCmd(List<String> files) implements OperationCmd {

    public static final Operation EDIT = Operation.of("log.title.edit", "error.edit.failed");

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
        return env.terminal().handOver(() -> env.git().edit(files)).fold(this::failedWith, this::succeeded);
    }
}
