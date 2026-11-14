package net.vanfleteren.jazygit.state.view;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

/**
 * The user asked to give the focus to the command log, to scroll through all it logged (ADR 129). The focus is view
 * state, so the app moves it itself before the message reaches the update, which leaves the model as it is.
 */
public record CommandLogFocused() implements Msg {

    @Override
    public Update.Next apply(Model model) {
        return Update.Next.of(model);
    }
}
