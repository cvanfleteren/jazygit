package net.vanfleteren.jazygit.feature.branch.rebase;

import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking how to rebase the checked out {@code branch} onto {@code onto}.
 */
public record RebasePopup(BranchName branch, BranchName onto) implements Popup {}
