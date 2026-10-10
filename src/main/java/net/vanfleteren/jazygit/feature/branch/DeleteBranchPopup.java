package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking where to delete {@code branch}.
 */
public record DeleteBranchPopup(String branch) implements Popup {
}
