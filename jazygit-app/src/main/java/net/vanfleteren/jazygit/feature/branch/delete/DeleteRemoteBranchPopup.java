package net.vanfleteren.jazygit.feature.branch.delete;

import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.popup.Popup;

/**
 * Asking to confirm deleting {@code branch} on its remote.
 */
public record DeleteRemoteBranchPopup(RemoteBranch branch) implements Popup {}
