package net.vanfleteren.jazygit.features.github.settings;

import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking which of the accounts gh is logged in to the repository uses.
 */
public record GitHubAccountPopup(GitHubSetup gitHub) implements Popup {}
