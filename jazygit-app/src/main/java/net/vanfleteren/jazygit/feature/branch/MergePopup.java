package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking how to merge {@code branch} into the checked out branch {@code into}.
 */
public record MergePopup(Branch.Name into, Branch.Name branch) implements Popup {}
