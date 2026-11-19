package net.vanfleteren.jazygit.features.branches.worktree.prune;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Forgetting the worktrees whose directory is gone.
 */
public final class PruneWorktreesAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> keys.add(
            Scopes.WORKTREES, Binding.always("p", "branches.help.worktrees.prune", PruneWorktreesMsg.Requested::new)));

    private PruneWorktreesAction() {}
}
