package net.vanfleteren.jazygit.feature.branch.push;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Pushing the selected branch.
 */
public final class PushAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(Scopes.BRANCHES, Binding.onSelected("P", "help.branches.push", PushMsg.Requested::new)),
            PushDialogs::popups);

    private PushAction() {}
}
