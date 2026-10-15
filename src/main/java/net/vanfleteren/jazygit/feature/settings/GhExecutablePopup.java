package net.vanfleteren.jazygit.feature.settings;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking which gh executable to run.
 *
 * @param current the one chosen now; empty for the one on the PATH
 */
public record GhExecutablePopup(String current) implements Popup {}
