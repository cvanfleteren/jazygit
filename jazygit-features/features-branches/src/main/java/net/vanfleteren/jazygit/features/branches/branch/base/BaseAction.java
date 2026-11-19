package net.vanfleteren.jazygit.features.branches.branch.base;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Setting the branch the selected branch is built on (ADR 143).
 */
public final class BaseAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.BRANCHES, Binding.onSelected("b", "branches.help.branches.base", BaseMsg.Requested::new)),
            BaseDialogs::popups);

    private BaseAction() {}
}
