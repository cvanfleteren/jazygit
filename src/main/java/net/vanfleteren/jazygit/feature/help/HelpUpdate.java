package net.vanfleteren.jazygit.feature.help;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;

import java.util.Optional;

/**
 * Showing the keybindings of a panel.
 */
public final class HelpUpdate {

    private HelpUpdate() {
    }

    public static Next update(Model model, HelpMsg msg) {
        return switch (msg) {
            case HelpMsg.Requested(HelpTopic topic) -> Next.of(model.withHelp(Optional.of(topic)));
            case HelpMsg.Closed() -> Next.of(model.withHelp(Optional.empty()));
        };
    }
}
