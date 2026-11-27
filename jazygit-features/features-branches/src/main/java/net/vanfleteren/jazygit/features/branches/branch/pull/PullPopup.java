package net.vanfleteren.jazygit.features.branches.branch.pull;

import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking how to pull {@code branch}, the checked out branch, which has diverged from its upstream while neither
 * {@code branch.<name>.rebase}, {@code pull.rebase} nor {@code pull.ff} says what to do: such a pull is refused. The
 * choice is not remembered.
 */
public record PullPopup(BranchName branch) implements Popup {}
