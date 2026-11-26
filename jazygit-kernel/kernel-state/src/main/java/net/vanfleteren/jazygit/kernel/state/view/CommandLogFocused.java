package net.vanfleteren.jazygit.kernel.state.view;

import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.Update;

/**
 * The user asked to give the focus to the command log, to scroll through all it logged (ADR 129). The focus is view
 * state, so the update leaves the model as it is and asks the app to move it (ADR 207).
 */
public record CommandLogFocused() implements Msg {

    @Override
    public Update.Next apply(Model model) {
        return Update.Next.of(model).asking(new ViewIntent.ToCommandLog());
    }
}
