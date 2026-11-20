package net.vanfleteren.jazygit.features.changes.inprogress;

import net.vanfleteren.jazygit.core.repo.InProgress;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking whether to continue, abort or skip {@code inProgress}, with {@code conflicts} files still conflicted.
 */
public record InProgressPopup(InProgress inProgress, int conflicts) implements Popup {}
