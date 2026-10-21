package net.vanfleteren.jazygit.feature.worktree.prune;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Forgetting the worktrees whose directory is gone.
 */
public final class PruneWorktreesAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys ->
            keys.add(Scopes.WORKTREES, Binding.always("p", "help.worktrees.prune", PruneWorktreesMsg.Requested::new)));

    private PruneWorktreesAction() {}
}
