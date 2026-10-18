package net.vanfleteren.jazygit.feature.branch.movecommits;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking for the name of the new branch the commits of {@code request} move to.
 */
public record MoveNamePopup(MoveRequest request) implements Popup {}
