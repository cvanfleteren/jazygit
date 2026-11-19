package net.vanfleteren.jazygit.features.changes.commit.create;

import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking to stage all files, because none is staged yet.
 */
public record StageAllPopup() implements Popup {}
