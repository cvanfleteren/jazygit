package net.vanfleteren.jazygit.feature.commit.create;

import net.vanfleteren.jazygit.state.popup.Popup;

/**
 * Asking to stage all files, because none is staged yet.
 */
public record StageAllPopup() implements Popup {}
