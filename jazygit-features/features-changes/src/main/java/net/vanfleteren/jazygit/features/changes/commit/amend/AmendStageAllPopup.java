package net.vanfleteren.jazygit.features.changes.commit.amend;

import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking whether to amend {@code target} with all files, because none is staged.
 */
public record AmendStageAllPopup(AmendTarget target) implements Popup {}
