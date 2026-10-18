package net.vanfleteren.jazygit.feature.branch.delete;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Deleting the selected branch, local or remote.
 */
public final class DeleteBranchAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                            Scopes.BRANCHES,
                            Binding.onSelected("d", "help.branches.delete", DeleteBranchMsg.Requested::new))
                    .add(
                            Scopes.REMOTE_BRANCHES,
                            Binding.onSelected(
                                    "d", "help.remoteBranches.delete", DeleteBranchMsg.RemoteRequested::new)),
            DeleteBranchDialogs::popups);

    private DeleteBranchAction() {}
}
