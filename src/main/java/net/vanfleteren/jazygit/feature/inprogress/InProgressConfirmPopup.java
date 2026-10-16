package net.vanfleteren.jazygit.feature.inprogress;

import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.InProgressAction;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking to confirm {@code action}, an abort or a skip of {@code inProgress}, which throws work away.
 */
public record InProgressConfirmPopup(InProgress inProgress, InProgressAction action) implements Popup {}
