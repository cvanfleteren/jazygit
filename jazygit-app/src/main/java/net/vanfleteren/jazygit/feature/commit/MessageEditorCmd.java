package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Opens {@code message} in git's editor, with the terminal handed over to it, and reports how it was saved.
 */
public record MessageEditorCmd(CommitMessage message) implements Cmd {

    @Override
    public Msg run(Env env) {
        return env.terminal()
                .handOver(() -> env.git().editMessage(message))
                .fold(this::failedWith, edited -> new MessageEditorMsg.Edited(edited._1, edited._2));
    }

    @Override
    public Msg failed(Failure failure) {
        return new MessageEditorMsg.Failed(failure);
    }
}
