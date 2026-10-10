package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking to stage all files, because none is staged yet.
 */
public record StageAllPopup() implements Popup {
}
