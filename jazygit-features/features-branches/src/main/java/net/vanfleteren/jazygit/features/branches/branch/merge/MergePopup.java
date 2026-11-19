package net.vanfleteren.jazygit.features.branches.branch.merge;

import net.vanfleteren.jazygit.core.git.model.BranchName;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking how to merge {@code branch} into the checked out branch {@code into}.
 */
public record MergePopup(BranchName into, BranchName branch) implements Popup {}
