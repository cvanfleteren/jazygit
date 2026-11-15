package net.vanfleteren.jazygit.branches.worktree.switchto;

import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Switching to the selected worktree.
 */
public final class SwitchWorktreeAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> keys.add(
            Scopes.WORKTREES,
            Binding.onSelected(
                    " ", "help.worktrees.switch", worktree -> new SwitchWorktreeMsg.Requested(worktree.location()))));

    private SwitchWorktreeAction() {}
}
