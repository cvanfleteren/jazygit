package net.vanfleteren.jazygit.feature.diff;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface DiffOptionsMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return DiffOptionsUpdate.update(model, this);
    }

    /**
     * The user asked to show changes to whitespace only, or to leave them out.
     */
    record WhitespaceToggled() implements DiffOptionsMsg {}
}
