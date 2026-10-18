package net.vanfleteren.jazygit.feature.commit.reset;

import net.vanfleteren.jazygit.git.model.ResetTarget;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking how to reset the checked out branch to {@code target}.
 *
 * @param dropped how many commits of the checked out branch the reset leaves behind, as far as is known
 */
public record ResetPopup(ResetTarget target, int dropped) implements Popup {

    /**
     * Asking how to reset to {@code target}, which leaves no commits behind that are known of.
     */
    public ResetPopup(ResetTarget target) {
        this(target, 0);
    }
}
