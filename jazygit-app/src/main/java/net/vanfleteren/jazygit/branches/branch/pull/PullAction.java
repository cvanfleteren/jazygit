package net.vanfleteren.jazygit.branches.branch.pull;

import net.vanfleteren.jazygit.ui.keys.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Pulling the selected branch, or fast-forwarding it.
 */
public final class PullAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(
            keys -> keys.add(Scopes.BRANCHES, Binding.onSelected("p", "help.branches.pull", PullMsg.Requested::new)));

    private PullAction() {}
}
