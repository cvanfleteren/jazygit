package net.vanfleteren.jazygit.feature.commit.revert;

import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking to confirm reverting {@code commit}.
 */
public record RevertPopup(Commit commit) implements Popup {}
