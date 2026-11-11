package net.vanfleteren.jazygit.feature.settings;

import net.vanfleteren.jazygit.state.popup.Popup;

/**
 * Asking which of the accounts gh is logged in to the repository uses.
 */
public record GitHubAccountPopup(GitHubSetup gitHub) implements Popup {}
