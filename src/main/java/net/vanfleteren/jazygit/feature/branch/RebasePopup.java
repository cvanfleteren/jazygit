package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking how to rebase the checked out {@code branch} onto {@code onto}.
 */
public record RebasePopup(String branch, String onto) implements Popup {
}
