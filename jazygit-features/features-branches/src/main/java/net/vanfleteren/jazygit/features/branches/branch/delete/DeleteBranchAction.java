package net.vanfleteren.jazygit.features.branches.branch.delete;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Deleting the selected branch, local or remote.
 */
public final class DeleteBranchAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                            Scopes.BRANCHES,
                            Binding.onSelected("d", "branches.help.branches.delete", DeleteBranchMsg.Requested::new))
                    .add(
                            Scopes.REMOTE_BRANCHES,
                            Binding.onSelected(
                                    "d", "branches.help.remoteBranches.delete", DeleteBranchMsg.RemoteRequested::new)),
            DeleteBranchDialogs::popups);

    private DeleteBranchAction() {}
}
