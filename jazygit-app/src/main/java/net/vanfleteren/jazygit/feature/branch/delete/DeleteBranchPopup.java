package net.vanfleteren.jazygit.feature.branch.delete;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking where to delete {@code branch}.
 */
public record DeleteBranchPopup(Branch.Name branch) implements Popup {}
