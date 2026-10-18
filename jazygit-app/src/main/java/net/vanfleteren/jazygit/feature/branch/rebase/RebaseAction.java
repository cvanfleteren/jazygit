package net.vanfleteren.jazygit.feature.branch.rebase;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Rebasing the checked out branch onto the selected one.
 */
public final class RebaseAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.BRANCHES, Binding.onSelected("r", "help.branches.rebase", RebaseMsg.Requested::new)),
            RebaseDialogs::popups);

    private RebaseAction() {}
}
