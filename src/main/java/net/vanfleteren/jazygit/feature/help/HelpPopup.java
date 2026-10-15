package net.vanfleteren.jazygit.feature.help;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Listing the keybindings of the panel with the id {@code scopeId}, and the global ones.
 *
 * @param actions what each key of the panel sends for what was selected in it when the help opened, in the order
 *                the keys are listed; none for a key that does nothing then
 */
public record HelpPopup(String scopeId, List<Option<Msg>> actions) implements Popup {
}
