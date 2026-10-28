package net.vanfleteren.jazygit.feature.branch.nuke;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Worktree;
import net.vanfleteren.jazygit.state.Popup;

/**
 * Asking to confirm nuking {@code branch}.
 *
 * @param remote   its remote branch that goes with it, if one does: on {@code origin}, of its own name
 * @param worktree the other worktree that has it checked out, which goes with it, if one does
 */
public record NukeBranchPopup(BranchName branch, Option<RemoteBranch> remote, Option<Worktree> worktree)
        implements Popup {

    /**
     * The nuke this popup asks about.
     */
    public NukeBranchCmd cmd() {
        return new NukeBranchCmd(branch, worktree.map(Worktree::location), remote);
    }
}
