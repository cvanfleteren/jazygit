package net.vanfleteren.jazygit.branches.worktree.prune;

import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Forgetting the worktrees whose directory is gone.
 */
public final class PruneWorktreesAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys ->
            keys.add(Scopes.WORKTREES, Binding.always("p", "help.worktrees.prune", PruneWorktreesMsg.Requested::new)));

    private PruneWorktreesAction() {}
}
