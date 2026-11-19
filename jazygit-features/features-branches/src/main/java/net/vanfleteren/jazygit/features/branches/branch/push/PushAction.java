package net.vanfleteren.jazygit.features.branches.branch.push;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Pushing the selected branch.
 */
public final class PushAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.BRANCHES, Binding.onSelected("P", "branches.help.branches.push", PushMsg.Requested::new)),
            PushDialogs::popups);

    private PushAction() {}
}
