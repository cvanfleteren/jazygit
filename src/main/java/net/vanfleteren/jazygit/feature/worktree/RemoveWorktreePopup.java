package net.vanfleteren.jazygit.feature.worktree;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking to confirm removing the worktree at {@code location}.
 *
 * @param name the name of its directory
 */
public record RemoveWorktreePopup(String location, String name) implements Popup {
}
