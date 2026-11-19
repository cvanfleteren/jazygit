package net.vanfleteren.jazygit.features.changes.commit.amend;

import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking to confirm amending {@code target} with the staged changes.
 */
public record AmendPopup(AmendTarget target) implements Popup {}
