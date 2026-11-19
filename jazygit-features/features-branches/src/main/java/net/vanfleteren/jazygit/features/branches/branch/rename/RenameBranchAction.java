package net.vanfleteren.jazygit.features.branches.branch.rename;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Renaming the selected branch.
 */
public final class RenameBranchAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.BRANCHES,
                    Binding.onSelected("R", "branches.help.branches.rename", RenameBranchMsg.Requested::new)),
            RenameBranchDialogs::popups);

    private RenameBranchAction() {}
}
