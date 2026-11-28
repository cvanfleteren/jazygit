package net.vanfleteren.jazygit.features.branches.branch.merge;

import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking how to merge {@code branch} into the checked out branch {@code into}.
 */
public record MergePopup(BranchName into, BranchRef branch) implements Popup {}
