package net.vanfleteren.jazygit.feature.inprogress;

import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking whether to continue, abort or skip {@code inProgress}, with {@code conflicts} files still conflicted.
 */
public record InProgressPopup(InProgress inProgress, int conflicts) implements Popup {}
