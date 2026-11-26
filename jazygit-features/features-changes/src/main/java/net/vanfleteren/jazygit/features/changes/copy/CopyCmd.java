package net.vanfleteren.jazygit.features.changes.copy;

import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Asks the terminal to copy {@code text} to the clipboard (ADR 94); {@code copied} tells the user what it was, once it
 * could ask.
 */
public record CopyCmd(String text, CopyNotice copied) implements Cmd {

    @Override
    public Msg run(Env env) {
        return new CopyMsg.Done(
                switch (env.terminal().copy(text)) {
                    case ASKED -> copied;
                    case NOT_OWNED -> new CopyNotice.NotCopied();
                    case FAILED -> new CopyNotice.CopyFailed();
                });
    }

    @Override
    public Msg failed(Failure failure) {
        return new CopyMsg.Done(new CopyNotice.CopyFailed());
    }
}
