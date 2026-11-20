package net.vanfleteren.jazygit.features.branches.branch.checkout;

import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Telling that {@code branch}, which is to be checked out, is checked out in the worktree at {@code worktree}
 * already, and asking to switch to that worktree instead.
 */
public record SwitchWorktreePopup(BranchName branch, String worktree) implements Popup {}
