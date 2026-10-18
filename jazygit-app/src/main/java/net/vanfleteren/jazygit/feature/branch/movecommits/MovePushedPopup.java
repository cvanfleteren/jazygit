package net.vanfleteren.jazygit.feature.branch.movecommits;

import net.vanfleteren.jazygit.state.Popup;

/**
 * Warning that commits of {@code request} are pushed already, and asking whether to force-push the branch they
 * are taken off, once they moved to {@code target}.
 */
public record MovePushedPopup(MoveRequest request, MoveTarget target) implements Popup {}
