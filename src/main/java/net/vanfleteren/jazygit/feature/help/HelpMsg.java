package net.vanfleteren.jazygit.feature.help;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface HelpMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return HelpUpdate.update(model, this);
    }

    /**
     * The user asked to see the keybindings of a panel.
     */
    record Requested(HelpTopic topic) implements HelpMsg {
    }

    record Closed() implements HelpMsg {
    }
}
