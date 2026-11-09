package net.vanfleteren.jazygit.feature.copy;

import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Notice;

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
