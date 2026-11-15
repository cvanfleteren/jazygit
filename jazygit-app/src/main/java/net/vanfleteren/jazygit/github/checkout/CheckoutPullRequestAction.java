package net.vanfleteren.jazygit.github.checkout;

import net.vanfleteren.jazygit.ui.keys.FeatureAction;
import net.vanfleteren.jazygit.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Checking out the pull request selected on the pull requests screen, in place or in a new worktree.
 */
public final class CheckoutPullRequestAction {

    public static final FeatureAction ACTION = FeatureAction.ofKeys(keys -> Scopes.PULL_REQUESTS.foldLeft(
            keys,
            (added, scope) -> added.add(
                    scope,
                    Binding.onSelected(" ", "help.prs.checkout", CheckoutPullRequestMsg.Requested::new),
                    Binding.onSelected(
                            "w", "help.prs.checkoutWorktree", CheckoutPullRequestMsg.WorktreeRequested::new))));

    private CheckoutPullRequestAction() {}
}
