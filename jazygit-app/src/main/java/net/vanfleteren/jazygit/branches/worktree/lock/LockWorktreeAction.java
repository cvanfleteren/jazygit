package net.vanfleteren.jazygit.branches.worktree.lock;

import net.vanfleteren.jazygit.ui.keys.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Locking the selected worktree, or unlocking it when it is locked.
 */
public final class LockWorktreeAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> keys.add(
            Scopes.WORKTREES,
            Binding.onSelected(
                    "L", "help.worktrees.lock", worktree -> new LockWorktreeMsg.Requested(worktree.location()))));

    private LockWorktreeAction() {}
}
