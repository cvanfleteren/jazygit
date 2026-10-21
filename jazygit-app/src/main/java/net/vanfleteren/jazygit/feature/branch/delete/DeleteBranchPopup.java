package net.vanfleteren.jazygit.feature.branch.delete;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking where to delete {@code branch}.
 *
 * @param worktree the name of the other worktree that has it checked out, if one does and it can be removed
 */
public record DeleteBranchPopup(Branch.Name branch, Option<String> worktree) implements Popup {

    /**
     * Asking where to delete {@code branch}, which no other worktree has checked out.
     */
    public DeleteBranchPopup(Branch.Name branch) {
        this(branch, Option.none());
    }
}
