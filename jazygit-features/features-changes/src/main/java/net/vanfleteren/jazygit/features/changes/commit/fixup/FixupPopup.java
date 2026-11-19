package net.vanfleteren.jazygit.features.changes.commit.fixup;

import net.vanfleteren.jazygit.core.git.model.Commit;
import net.vanfleteren.jazygit.core.git.model.Sha;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking how to fix up {@code commit} into its parent {@code into}, which need not be among the commits read: the
 * search can find commits older than the log reads.
 */
public record FixupPopup(Commit commit, Sha into) implements Popup {}
