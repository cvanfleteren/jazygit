package net.vanfleteren.jazygit.branches.branch.push;

import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

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
