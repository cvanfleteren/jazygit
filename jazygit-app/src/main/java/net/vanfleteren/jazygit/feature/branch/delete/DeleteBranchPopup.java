package net.vanfleteren.jazygit.feature.branch.delete;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking where to delete {@code branch}.
 *
 * @param remote   the remote branch that deleting it on its remote deletes, which the dialog names: its upstream,
 *                 which may have another name, or else the branch of the same name on the default remote
 * @param worktree the name of the other worktree that has it checked out, if one does and it can be removed, once
 *                 unlocked when it is locked
 */
public record DeleteBranchPopup(BranchName branch, RemoteBranch remote, Option<String> worktree) implements Popup {

    /**
     * Asking where to delete {@code branch}, which no other worktree has checked out, and which is on the default
     * remote under its own name, if at all.
     */
    public DeleteBranchPopup(BranchName branch) {
        this(branch, new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, branch), Option.none());
    }

    /**
     * Whether the remote branch has another name than {@code branch}: it is then another branch, which the dialog
     * says.
     */
    public boolean remoteNamedOtherwise() {
        return !remote.branch().equals(branch);
    }
}
