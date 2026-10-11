package net.vanfleteren.jazygit.feature.theme;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking which theme to use, while {@code current} is in use.
 */
public record ThemePopup(String current) implements Popup {
}
