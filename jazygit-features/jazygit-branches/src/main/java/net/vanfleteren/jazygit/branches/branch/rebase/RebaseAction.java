package net.vanfleteren.jazygit.branches.branch.rebase;

import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Rebasing the checked out branch onto the selected one, or on its base branch (ADR 91).
 */
public final class RebaseAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.BRANCHES, Binding.onSelected("r", "help.branches.rebase", RebaseMsg.Requested::new)),
            RebaseDialogs::popups);

    private RebaseAction() {}
}
