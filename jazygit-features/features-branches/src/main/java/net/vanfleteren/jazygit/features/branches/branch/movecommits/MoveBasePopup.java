package net.vanfleteren.jazygit.features.branches.branch.movecommits;

import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking where the new branch {@code name}, which the commits of {@code request} move to, starts.
 */
public record MoveBasePopup(MoveRequest request, BranchName name) implements Popup {}
