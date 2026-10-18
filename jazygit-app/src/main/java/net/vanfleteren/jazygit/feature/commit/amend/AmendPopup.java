package net.vanfleteren.jazygit.feature.commit.amend;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking to confirm amending {@code target} with the staged changes.
 */
public record AmendPopup(AmendTarget target) implements Popup {}
