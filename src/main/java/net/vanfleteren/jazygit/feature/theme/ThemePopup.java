package net.vanfleteren.jazygit.feature.theme;

import net.vanfleteren.jazygit.settings.ThemeName;
import net.vanfleteren.jazygit.state.Popup;

import io.vavr.control.Option;

/**
 * Asking which theme to use, while {@code current} is in use. The highlighted theme shows in its place until
 * one is chosen.
 *
 * @param overridden the theme asked for at startup when the popup opened, which cancelling brings back
 */
public record ThemePopup(ThemeName current, Option<ThemeName> overridden) implements Popup {
}
