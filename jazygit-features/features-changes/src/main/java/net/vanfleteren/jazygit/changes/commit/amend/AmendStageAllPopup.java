package net.vanfleteren.jazygit.changes.commit.amend;

import net.vanfleteren.jazygit.state.popup.Popup;

/**
 * Asking whether to amend {@code target} with all files, because none is staged.
 */
public record AmendStageAllPopup(AmendTarget target) implements Popup {}
