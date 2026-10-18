package net.vanfleteren.jazygit.feature.branch.movecommits;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Warning that commits of {@code request} are pushed already, and asking whether to force-push the branch they
 * are taken off, once they moved to the new branch {@code name}, which starts at {@code base}.
 */
public record MovePushedPopup(MoveRequest request, Branch.Name name, MoveBase base) implements Popup {}
