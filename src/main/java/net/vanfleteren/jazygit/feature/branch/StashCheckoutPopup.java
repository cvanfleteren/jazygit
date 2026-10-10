package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking whether to stash the uncommitted changes, check out {@code branch} and pop the stash again.
 */
public record StashCheckoutPopup(String branch) implements Popup {
}
