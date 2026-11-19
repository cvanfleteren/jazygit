package net.vanfleteren.jazygit.features.branches.worktree.lock;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Locking the selected worktree, or unlocking it when it is locked.
 */
public final class LockWorktreeAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> keys.add(
            Scopes.WORKTREES,
            Binding.onSelected(
                    "L",
                    "branches.help.worktrees.lock",
                    worktree -> new LockWorktreeMsg.Requested(worktree.location()))));

    private LockWorktreeAction() {}
}
