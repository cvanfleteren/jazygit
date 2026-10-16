package net.vanfleteren.jazygit.feature.stash;

import net.vanfleteren.jazygit.git.model.Stash;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking to confirm the deletion of {@code stash}.
 */
public record StashDropPopup(Stash stash) implements Popup {}
