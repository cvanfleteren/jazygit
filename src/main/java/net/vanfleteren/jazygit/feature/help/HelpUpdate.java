package net.vanfleteren.jazygit.feature.help;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Showing the keybindings of a panel.
 */
public final class HelpUpdate {

    private HelpUpdate() {
    }

    public static Next update(Model model, HelpMsg msg) {
        return switch (msg) {
            case HelpMsg.Requested(HelpTopic topic) -> Next.of(model.openPopup(new HelpPopup(topic)));
            case HelpMsg.Closed() -> Next.of(model.withoutPopup(HelpPopup.class));
        };
    }
}
