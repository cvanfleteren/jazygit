package net.vanfleteren.jazygit.feature.branch.merge;

import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking how to merge {@code branch} into the checked out branch {@code into}.
 */
public record MergePopup(BranchName into, BranchName branch) implements Popup {}
