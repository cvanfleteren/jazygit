package net.vanfleteren.jazygit.branches.branch.checkout;

import net.vanfleteren.jazygit.ui.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Checking out the selected branch, local or remote.
 */
public final class CheckoutAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                            Scopes.BRANCHES,
                            Binding.onSelected(" ", "branches.help.branches.checkout", CheckoutMsg.Requested::new))
                    .add(
                            Scopes.REMOTE_BRANCHES,
                            Binding.onSelected(
                                    " ", "branches.help.remoteBranches.checkout", CheckoutMsg.RemoteRequested::new))
                    .add(
                            Scopes.REMOTE_BRANCHES,
                            Binding.onSelected(
                                    "w",
                                    "branches.help.remoteBranches.worktree",
                                    CheckoutMsg.RemoteInNewWorktreeRequested::new)),
            CheckoutDialogs::popups);

    private CheckoutAction() {}
}
