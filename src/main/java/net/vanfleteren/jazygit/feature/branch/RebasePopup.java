package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking how to rebase the checked out {@code branch} onto {@code onto}.
 */
public record RebasePopup(Branch.Name branch, Branch.Name onto) implements Popup {
}
