package net.vanfleteren.jazygit.feature.commit.amend;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking whether to amend {@code target} with all files, because none is staged.
 */
public record AmendStageAllPopup(AmendTarget target) implements Popup {}
