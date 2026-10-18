package net.vanfleteren.jazygit.feature.worktree.switchto;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

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
