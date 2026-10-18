package net.vanfleteren.jazygit.feature.worktree.add;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Checking the selected branch out in a new worktree.
 */
public final class AddWorktreeAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.BRANCHES,
                    Binding.onSelected("w", "help.branches.newWorktree", AddWorktreeMsg.Requested::new)),
            AddWorktreeDialogs::popups);

    private AddWorktreeAction() {}
}
