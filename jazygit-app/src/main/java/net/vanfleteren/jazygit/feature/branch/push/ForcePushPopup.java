package net.vanfleteren.jazygit.feature.branch.push;

import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking to confirm a force push of the diverged {@code branch}.
 */
public record ForcePushPopup(BranchName branch) implements Popup {}
