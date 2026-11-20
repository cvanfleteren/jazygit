package net.vanfleteren.jazygit.features.branches.branch.delete;

import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking to confirm deleting {@code branch} on its remote.
 */
public record DeleteRemoteBranchPopup(RemoteBranch branch) implements Popup {}
