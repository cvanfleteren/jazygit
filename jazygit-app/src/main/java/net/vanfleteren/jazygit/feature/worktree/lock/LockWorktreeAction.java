package net.vanfleteren.jazygit.feature.worktree.lock;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

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
