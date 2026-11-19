package net.vanfleteren.jazygit.state.view;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * The user asked to move to the file {@code step} of the one highlighted in the files pane, from the content panel
 * (ADR 117). The highlight of the files pane is view state, so the app moves it itself before the message reaches the
 * update, which leaves the model as it is.
 */
public record FileStepMsg(FileStepMsg.Step step) implements Msg {

    /**
     * Which way the highlight moves.
     */
    public enum Step {
        NEXT,
        PREVIOUS
    }

    @Override
    public Update.Next apply(Model model) {
        return Update.Next.of(model);
    }
}
