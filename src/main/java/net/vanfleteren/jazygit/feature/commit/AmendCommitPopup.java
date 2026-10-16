package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking to confirm amending {@code commit} with the staged changes.
 */
public record AmendCommitPopup(Commit commit) implements Popup {}
