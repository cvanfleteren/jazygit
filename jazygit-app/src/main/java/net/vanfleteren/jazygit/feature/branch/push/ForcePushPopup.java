package net.vanfleteren.jazygit.feature.branch.push;

import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.popup.Popup;

/**
 * Asking to confirm a force push of {@code branch}, which has the {@code behind} commits of its remote branch that it
 * does not have itself, and {@code ahead} commits of its own: a force push drops the first.
 */
public record ForcePushPopup(BranchName branch, int ahead, int behind) implements Popup {

    /**
     * Whether the branch has no commits of its own to push, so all a force push does is drop those of the remote.
     */
    public boolean onlyBehind() {
        return ahead == 0;
    }
}
