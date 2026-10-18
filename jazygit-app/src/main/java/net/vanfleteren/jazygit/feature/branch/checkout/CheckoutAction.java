package net.vanfleteren.jazygit.feature.branch.checkout;

import net.vanfleteren.jazygit.feature.FeatureAction;
import net.vanfleteren.jazygit.keys.Keymap.Binding;
import net.vanfleteren.jazygit.keys.Scopes;

/**
 * Checking out the selected branch, local or remote.
 */
public final class CheckoutAction {

    public static final FeatureAction ACTION = new FeatureAction(
            keys -> keys.add(
                            Scopes.BRANCHES,
                            Binding.onSelected(" ", "help.branches.checkout", CheckoutMsg.Requested::new))
                    .add(
                            Scopes.REMOTE_BRANCHES,
                            Binding.onSelected(" ", "help.remoteBranches.checkout", CheckoutMsg.RemoteRequested::new)),
            CheckoutDialogs::popups);

    private CheckoutAction() {}
}
