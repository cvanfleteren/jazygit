package net.vanfleteren.jazygit.feature.branch.movecommits;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking where the commits of {@code request} move to: a new branch, or one of {@code branches}.
 *
 * @param branches the local branches other than the checked out one, the most recently committed to first
 */
public record MoveTargetPopup(MoveRequest request, List<Branch> branches) implements Popup {}
