package net.vanfleteren.jazygit.features.changes.commit;

import net.vanfleteren.jazygit.core.git.model.CommitMessage;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;

/**
 * Opens {@code message} in git's editor, with the terminal handed over to it, and reports how it was saved.
 */
public record MessageEditorCmd(CommitMessage message) implements Cmd {

    public static final Operation EDIT_MESSAGE =
            Operation.of("changes.log.title.editMessage", "changes.error.editMessage.failed");

    @Override
    public Msg run(Env env) {
        return env.terminal()
                .handOver(() -> env.git().editor().editMessage(message))
                .mapLeft(Failure::of)
                .fold(this::failed, edited -> new MessageEditorMsg.Edited(edited._1, edited._2));
    }

    @Override
    public Msg failed(Failure failure) {
        return new MessageEditorMsg.Failed(failure);
    }
}
