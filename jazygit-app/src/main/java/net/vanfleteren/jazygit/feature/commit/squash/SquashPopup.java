package net.vanfleteren.jazygit.feature.commit.squash;

import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking to confirm squashing {@code commit} into its parent {@code into}, which need not be among the commits read: the
 * search can find commits older than the log reads.
 */
public record SquashPopup(Commit commit, Sha into) implements Popup {}
