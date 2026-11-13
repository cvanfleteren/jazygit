package net.vanfleteren.jazygit.feature.branch.push;

import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.popup.Popup;

/**
 * Asking to confirm a force push of {@code branch}, which lacks the {@code behind} commits of its remote branch, and
 * has {@code ahead} commits of its own: a force push drops the first. It is only asked when the branch once had them
 * (ADR 116).
 */
public record ForcePushPopup(BranchName branch, int ahead, int behind) implements Popup {

    /**
     * Whether the branch has no commits of its own to push, so all a force push does is drop those of the remote.
     */
    public boolean onlyBehind() {
        return ahead == 0;
    }
}
