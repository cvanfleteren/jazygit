package net.vanfleteren.jazygit.branches.branch.base;

import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

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
