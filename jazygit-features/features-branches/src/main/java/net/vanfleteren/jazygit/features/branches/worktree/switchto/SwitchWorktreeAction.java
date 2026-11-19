package net.vanfleteren.jazygit.features.branches.worktree.switchto;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Switching to the selected worktree.
 */
public final class SwitchWorktreeAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> keys.add(
            Scopes.WORKTREES,
            Binding.onSelected(
                    " ",
                    "branches.help.worktrees.switch",
                    worktree -> new SwitchWorktreeMsg.Requested(worktree.location()))));

    private SwitchWorktreeAction() {}
}
