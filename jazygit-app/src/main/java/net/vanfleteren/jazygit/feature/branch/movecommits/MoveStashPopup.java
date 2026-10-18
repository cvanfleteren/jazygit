package net.vanfleteren.jazygit.feature.branch.movecommits;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking to take the uncommitted changes along, which are in the way of checking out the branch {@code cmd} moves
 * the commits to.
 */
public record MoveStashPopup(MoveCmd.Move cmd) implements Popup {}
