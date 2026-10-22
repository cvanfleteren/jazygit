package net.vanfleteren.jazygit.feature.branch.delete;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking whether to unlock the worktree at {@code location}, which has {@code branch} checked out, so that it can be
 * removed and the branch deleted.
 *
 * @param name   the name of its directory
 * @param reason why it was locked; empty when no reason was given
 */
public record UnlockWorktreePopup(Branch.Name branch, String location, String name, String reason) implements Popup {}
