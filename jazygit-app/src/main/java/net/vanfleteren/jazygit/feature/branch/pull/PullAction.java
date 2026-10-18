package net.vanfleteren.jazygit.feature.branch.pull;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Pulling the selected branch, or fast-forwarding it.
 */
public final class PullAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(
            keys -> keys.add(Scopes.BRANCHES, Binding.onSelected("p", "help.branches.pull", PullMsg.Requested::new)));

    private PullAction() {}
}
