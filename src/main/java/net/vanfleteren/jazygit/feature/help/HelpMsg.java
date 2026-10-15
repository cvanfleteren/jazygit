package net.vanfleteren.jazygit.feature.help;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface HelpMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return HelpUpdate.update(model, this);
    }

    /**
     * The user asked to see the keybindings of the panel with the id {@code scopeId}.
     *
     * @param actions what each key of the panel sends for what was selected in it, in the order the keys are
     *                listed; none for a key that does nothing then
     */
    record Requested(String scopeId, List<Option<Msg>> actions) implements HelpMsg {
    }

    record Closed() implements HelpMsg {
    }

    /**
     * The user chose to run the key that sends {@code msg} from the help.
     */
    record Chosen(Msg msg) implements HelpMsg {
    }
}
