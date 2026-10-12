package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking how to merge {@code branch} into the checked out branch {@code into}.
 */
public record MergePopup(String into, String branch) implements Popup {
}
