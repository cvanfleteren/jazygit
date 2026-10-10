package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking to confirm a force push of the diverged {@code branch}.
 */
public record ForcePushPopup(String branch) implements Popup {
}
