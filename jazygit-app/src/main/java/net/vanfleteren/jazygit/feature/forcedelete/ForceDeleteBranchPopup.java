package net.vanfleteren.jazygit.feature.forcedelete;

import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Warning that {@code lost} commits of {@code branch} are on no other branch, and asking to delete it anyway;
 * {@code gone} when its upstream was deleted on the remote, as after its pull request was squash merged.
 */
public record ForceDeleteBranchPopup(BranchName branch, int lost, boolean gone) implements Popup {}
