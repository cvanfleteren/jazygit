package net.vanfleteren.jazygit.features.changes.commit.fixup;

import net.vanfleteren.jazygit.core.repo.Commit;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Asking how to fix up {@code commit} into its parent {@code into}, which need not be among the commits read: the
 * search can find commits older than the log reads.
 */
public record FixupPopup(Commit commit, Sha into) implements Popup {}
