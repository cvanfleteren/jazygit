package net.vanfleteren.jazygit.feature.worktree.remove;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Warning that the worktree {@code removal} removes has changes, and asking to remove it anyway, deleting its branch
 * too when the removal does.
 *
 * @param name the name of its directory
 */
public record ForceRemoveWorktreePopup(RemoveWorktreeCmd removal, String name) implements Popup {}
