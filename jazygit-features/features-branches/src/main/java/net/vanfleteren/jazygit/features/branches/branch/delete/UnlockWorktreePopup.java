package net.vanfleteren.jazygit.features.branches.branch.delete;

import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking whether to unlock the worktree at {@code location}, which has {@code branch} checked out, so that it can be
 * removed and the branch deleted.
 *
 * @param name   the name of its directory
 * @param reason why it was locked; empty when no reason was given
 */
public record UnlockWorktreePopup(BranchName branch, String location, String name, String reason) implements Popup {}
