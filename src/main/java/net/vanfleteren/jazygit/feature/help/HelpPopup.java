package net.vanfleteren.jazygit.feature.help;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Listing the keybindings of the panel {@code topic}.
 */
public record HelpPopup(HelpTopic topic) implements Popup {
}
