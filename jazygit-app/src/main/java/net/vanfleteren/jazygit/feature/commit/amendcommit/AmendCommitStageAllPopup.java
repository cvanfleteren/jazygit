package net.vanfleteren.jazygit.feature.commit.amendcommit;

import net.vanfleteren.jazygit.git.model.Commit;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking whether to amend {@code commit} with all files, because none is staged.
 */
public record AmendCommitStageAllPopup(Commit commit) implements Popup {}
