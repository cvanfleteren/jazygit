package net.vanfleteren.jazygit.feature.stash;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking to confirm the deletion of the stash {@code ref}.
 */
public record StashDropPopup(String ref) implements Popup {
}
