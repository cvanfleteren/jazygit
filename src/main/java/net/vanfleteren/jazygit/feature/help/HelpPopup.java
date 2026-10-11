package net.vanfleteren.jazygit.feature.help;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Listing the keybindings of the panel with the id {@code scopeId}, and the global ones.
 */
public record HelpPopup(String scopeId) implements Popup {
}
