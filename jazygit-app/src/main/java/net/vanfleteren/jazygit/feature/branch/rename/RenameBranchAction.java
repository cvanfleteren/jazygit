package net.vanfleteren.jazygit.feature.branch.rename;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Renaming the selected branch.
 */
public final class RenameBranchAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.BRANCHES, Binding.onSelected("R", "help.branches.rename", RenameBranchMsg.Requested::new)),
            RenameBranchDialogs::popups);

    private RenameBranchAction() {}
}
