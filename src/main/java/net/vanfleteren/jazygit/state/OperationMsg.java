package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * The outcome of an {@link OperationCmd}.
 */
public sealed interface OperationMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return OperationUpdate.update(model, this);
    }

    record Succeeded(OperationCmd cmd, List<ExecutedCommand> commands) implements OperationMsg {
    }

    record Failed(OperationCmd cmd, Failure failure) implements OperationMsg {
    }
}
