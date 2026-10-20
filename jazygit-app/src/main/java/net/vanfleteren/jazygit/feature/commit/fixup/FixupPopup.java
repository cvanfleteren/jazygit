package net.vanfleteren.jazygit.feature.commit.fixup;

import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking how to fix up {@code commit} into its parent {@code into}, which need not be among the commits read: the
 * search can find commits older than the log reads.
 */
public record FixupPopup(Commit commit, Sha into) implements Popup {}
