package net.vanfleteren.jazygit.features.changes.copy;

import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Notice;

/**
 * Asks the terminal to copy {@code text} to the clipboard (ADR 94); {@code copied} tells the user what it was, once it
 * could ask.
 */
public record CopyCmd(String text, Notice copied) implements Cmd {

    @Override
    public Msg run(Env env) {
        return new CopyMsg.Done(
                switch (env.terminal().copy(text)) {
                    case ASKED -> copied;
                    case NOT_OWNED -> new Notice.NotCopied();
                    case FAILED -> new Notice.CopyFailed();
                });
    }

    @Override
    public Msg failed(Failure failure) {
        return new CopyMsg.Done(new Notice.CopyFailed());
    }
}
