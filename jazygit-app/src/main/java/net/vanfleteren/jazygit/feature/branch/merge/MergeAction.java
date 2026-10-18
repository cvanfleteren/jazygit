package net.vanfleteren.jazygit.feature.branch.merge;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Merging the selected branch into the checked out one.
 */
public final class MergeAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(Scopes.BRANCHES, Binding.onSelected("m", "help.branches.merge", MergeMsg.Requested::new)),
            MergeDialogs::popups);

    private MergeAction() {}
}
