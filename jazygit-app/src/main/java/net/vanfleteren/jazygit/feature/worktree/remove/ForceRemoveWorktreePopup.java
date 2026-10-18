package net.vanfleteren.jazygit.feature.worktree.remove;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Warning that the worktree at {@code location} has changes, and asking to remove it anyway.
 *
 * @param name the name of its directory
 */
public record ForceRemoveWorktreePopup(String location, String name) implements Popup {}
