package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking for the name of a new branch, which starts at {@code base}, shown to the user as {@code label}.
 */
public record NewBranchPopup(String base, String label) implements Popup {

    public NewBranchPopup(String base) {
        this(base, base);
    }
}
