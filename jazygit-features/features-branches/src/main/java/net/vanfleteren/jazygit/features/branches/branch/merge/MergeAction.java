package net.vanfleteren.jazygit.features.branches.branch.merge;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Merging the selected branch into the checked out one.
 */
public final class MergeAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.BRANCHES, Binding.onSelected("m", "branches.help.branches.merge", MergeMsg.Requested::new)),
            MergeDialogs::popups);

    private MergeAction() {}
}
