package net.vanfleteren.jazygit.feature.branch;

import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking to confirm a force push of the diverged {@code branch}.
 */
public record ForcePushPopup(Branch.Name branch) implements Popup {}
