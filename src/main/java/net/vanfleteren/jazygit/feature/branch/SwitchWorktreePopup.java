package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Telling that {@code branch}, which is to be checked out, is checked out in the worktree at {@code worktree}
 * already, and asking to switch to that worktree instead.
 */
public record SwitchWorktreePopup(Branch.Name branch, String worktree) implements Popup {
}
