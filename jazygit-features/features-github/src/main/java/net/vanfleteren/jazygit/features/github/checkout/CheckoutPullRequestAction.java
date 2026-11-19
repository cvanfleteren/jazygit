package net.vanfleteren.jazygit.features.github.checkout;

import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

/**
 * Checking out the pull request selected on the pull requests screen, in place or in a new worktree.
 */
public final class CheckoutPullRequestAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> Scopes.PULL_REQUESTS.foldLeft(
            keys,
            (added, scope) -> added.add(
                    scope,
                    Binding.onSelected(" ", "github.help.prs.checkout", CheckoutPullRequestMsg.Requested::new),
                    Binding.onSelected(
                            "w", "github.help.prs.checkoutWorktree", CheckoutPullRequestMsg.WorktreeRequested::new))));

    private CheckoutPullRequestAction() {}
}
