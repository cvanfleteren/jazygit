package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking for a new message of the last commit, starting out with its current one.
 */
public record RewordPopup(Commit last) implements Popup {
}
