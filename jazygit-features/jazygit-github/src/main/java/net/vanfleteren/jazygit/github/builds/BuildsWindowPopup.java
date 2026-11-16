package net.vanfleteren.jazygit.github.builds;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.popup.Popup;

/**
 * Asking how many of the newest builds show (ADR 131).
 *
 * @param window how many show now; none for all those read
 */
public record BuildsWindowPopup(Option<Integer> window) implements Popup {}
