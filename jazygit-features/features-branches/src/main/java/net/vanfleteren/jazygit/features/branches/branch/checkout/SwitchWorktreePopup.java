package net.vanfleteren.jazygit.features.branches.branch.checkout;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.Midway;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Telling that {@code branch}, which is to be checked out, is checked out in the worktree at {@code worktree}
 * already, and asking to switch to that worktree instead.
 *
 * @param midway what that worktree is in the middle of, if anything, which it says too (ADR 197)
 */
public record SwitchWorktreePopup(BranchName branch, String worktree, Option<Midway> midway) implements Popup {

    /**
     * Asking to switch to a worktree that is in the middle of nothing.
     */
    public SwitchWorktreePopup(BranchName branch, String worktree) {
        this(branch, worktree, Option.none());
    }
}
