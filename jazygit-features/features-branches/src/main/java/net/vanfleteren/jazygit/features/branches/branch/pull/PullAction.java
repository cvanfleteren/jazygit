package net.vanfleteren.jazygit.features.branches.branch.pull;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Pulling the selected branch, or fast-forwarding it.
 */
public final class PullAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys ->
            keys.add(Scopes.BRANCHES, Binding.onSelected("p", "branches.help.branches.pull", PullMsg.Requested::new)));

    private PullAction() {}
}
