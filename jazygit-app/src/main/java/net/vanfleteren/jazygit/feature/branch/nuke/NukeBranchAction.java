package net.vanfleteren.jazygit.feature.branch.nuke;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Nuking the selected local branch: deleting it, its worktree and its remote branch, after one confirmation.
 */
public final class NukeBranchAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                    Scopes.BRANCHES, Binding.onSelected("D", "help.branches.nuke", NukeBranchMsg.Requested::new)),
            NukeBranchDialogs::popups);

    private NukeBranchAction() {}
}
