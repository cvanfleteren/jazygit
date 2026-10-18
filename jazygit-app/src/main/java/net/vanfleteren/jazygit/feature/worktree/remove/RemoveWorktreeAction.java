package net.vanfleteren.jazygit.feature.worktree.remove;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Removing the selected worktree.
 */
public final class RemoveWorktreeAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.WORKTREES,
                    Binding.onSelected(
                            "d",
                            "help.worktrees.remove",
                            worktree -> new RemoveWorktreeMsg.Requested(worktree.location()))),
            RemoveWorktreeDialogs::popups);

    private RemoveWorktreeAction() {}
}
