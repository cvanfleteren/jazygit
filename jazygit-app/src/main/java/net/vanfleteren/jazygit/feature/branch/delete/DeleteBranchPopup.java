package net.vanfleteren.jazygit.feature.branch.delete;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking where to delete {@code branch}.
 *
 * @param worktree the name of the other worktree that has it checked out, if one does and it can be removed, once
 *                 unlocked when it is locked
 */
public record DeleteBranchPopup(BranchName branch, Option<String> worktree) implements Popup {

    /**
     * Asking where to delete {@code branch}, which no other worktree has checked out.
     */
    public DeleteBranchPopup(BranchName branch) {
        this(branch, Option.none());
    }
}
