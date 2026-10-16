package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking for a new message of a commit, starting out with its current one.
 */
public record RewordPopup(Commit commit) implements Popup {}
