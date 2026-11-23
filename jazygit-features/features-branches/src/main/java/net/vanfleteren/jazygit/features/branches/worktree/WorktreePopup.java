package net.vanfleteren.jazygit.features.branches.worktree;

import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * A popup about one worktree, which the worktrees highlight while it is open: the one it is about is the one the
 * user acts on next.
 */
public interface WorktreePopup extends Popup {

    /**
     * The root of the worktree it is about, as git lists it.
     */
    String location();
}
