package net.vanfleteren.jazygit.feature.branch.movecommits;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking where the new branch {@code name}, which the commits of {@code request} move to, starts.
 */
public record MoveBasePopup(MoveRequest request, Branch.Name name) implements Popup {}
