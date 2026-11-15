package net.vanfleteren.jazygit.branches.branch.merge;

import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Merging the selected branch into the checked out one.
 */
public final class MergeAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(Scopes.BRANCHES, Binding.onSelected("m", "help.branches.merge", MergeMsg.Requested::new)),
            MergeDialogs::popups);

    private MergeAction() {}
}
