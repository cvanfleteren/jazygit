package net.vanfleteren.jazygit.feature.help;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Showing the keybindings of a panel.
 */
public final class HelpUpdate {

    private HelpUpdate() {}

    public static Next update(Model model, HelpMsg msg) {
        return switch (msg) {
            case HelpMsg.Requested(String scopeId, List<Option<Msg>> actions) ->
                Next.of(model.openPopup(new HelpPopup(scopeId, actions)));
            // Closed and run in one step, so a popup the key opens replaces the help without a frame in between.
            case HelpMsg.Chosen(Msg chosen) -> Update.update(model.withoutPopup(HelpPopup.class), chosen);
        };
    }
}
