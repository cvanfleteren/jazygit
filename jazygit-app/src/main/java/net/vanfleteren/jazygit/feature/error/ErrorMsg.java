package net.vanfleteren.jazygit.feature.error;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface ErrorMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return ErrorUpdate.update(model, this);
    }

    /**
     * The user closed the error popup.
     */
    record Dismissed() implements ErrorMsg {}
}
