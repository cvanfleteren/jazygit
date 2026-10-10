package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking for the name of a new branch, which starts at {@code base}.
 */
public record NewBranchPopup(String base) implements Popup {
}
