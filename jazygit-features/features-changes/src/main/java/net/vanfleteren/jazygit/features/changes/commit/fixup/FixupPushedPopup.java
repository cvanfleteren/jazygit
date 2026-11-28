package net.vanfleteren.jazygit.features.changes.commit.fixup;

import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.commit.PushedWarning;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Warning that {@code commit} is pushed already, before asking how to fix it up into its parent {@code into}.
 */
public record FixupPushedPopup(Commit commit, Sha into, PushedWarning warning) implements Popup {

    /**
     * The popup that asks how to fix up the commit, once the user goes on.
     */
    public FixupPopup fixup() {
        return new FixupPopup(commit, into);
    }
}
