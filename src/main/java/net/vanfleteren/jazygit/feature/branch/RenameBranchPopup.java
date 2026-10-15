package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking for the new name of {@code branch}.
 */
public record RenameBranchPopup(Branch.Name branch) implements Popup {}
